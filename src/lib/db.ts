import "server-only";

import { Pool, type PoolClient } from "pg";
import { env } from "./env";

/**
 * The database: Postgres on Neon, through one pool per process.
 *
 * There is no schema yet — that comes with whatever Wink turns out to be.
 * When there is one, apply it from `ready()` so a fresh deployment needs
 * nothing but DATABASE_URL to be usable.
 *
 * The pool lives on `globalThis` because Next's dev server re-evaluates
 * modules on every edit, and a fresh pool per edit would leak connections.
 */

declare global {
  // eslint-disable-next-line no-var
  var __winkPool: Pool | undefined;
}

function connectionString(): string {
  const url = env.databaseUrlPooled || env.databaseUrl;
  if (!url) throw new Error("DATABASE_URL is not set. See .env.example.");
  return url;
}

function pool(): Pool {
  if (!globalThis.__winkPool) {
    const created = new Pool({
      connectionString: connectionString(),
      ssl: { rejectUnauthorized: true },
      max: 8,
      idleTimeoutMillis: 30_000,
    });
    // Neon closes connections that sit idle. An idle client in the pool then emits "error"; unhandled,
    // that takes the whole process down. The pool has already dropped the client, so noting it is enough.
    created.on("error", (error) => console.warn("[db] idle connection closed:", error.message));
    globalThis.__winkPool = created;
  }
  return globalThis.__winkPool;
}

/** A connection Neon closed under us: the query never ran, so it is safe to send again on a fresh one. */
function droppedConnection(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /Connection terminated|ECONNRESET|Client has encountered a connection error/i.test(message);
}

/**
 * One query, a read sent again once if the connection it got had been closed. A write is not: it may
 * have gone through before the connection dropped, and a second one would save it twice.
 */
async function query(text: string, params: Params) {
  try {
    return await pool().query(text, params as unknown[]);
  } catch (error) {
    if (!droppedConnection(error) || !/^\s*select\b/i.test(text)) throw error;
    return await pool().query(text, params as unknown[]);
  }
}

type Params = readonly unknown[];

/** All rows. */
export async function q<T>(text: string, params: Params = []): Promise<T[]> {
  const result = await query(text, params);
  return result.rows as T[];
}

/** The first row, or undefined. */
export async function one<T>(text: string, params: Params = []): Promise<T | undefined> {
  return (await q<T>(text, params))[0];
}

/** Rows affected. */
export async function run(text: string, params: Params = []): Promise<number> {
  const result = await query(text, params);
  return result.rowCount ?? 0;
}

/** Several statements, all or nothing. */
export async function tx<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool().connect();
  try {
    await client.query("BEGIN");
    const value = await fn(client);
    await client.query("COMMIT");
    return value;
  } catch (error) {
    await client.query("ROLLBACK").catch(() => null);
    throw error;
  } finally {
    client.release();
  }
}

/** A round trip, for the health check. */
export async function ping(): Promise<boolean> {
  const row = await one<{ ok: number }>("SELECT 1 AS ok");
  return row?.ok === 1;
}
