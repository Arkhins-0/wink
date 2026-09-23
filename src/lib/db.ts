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
    globalThis.__winkPool = new Pool({
      connectionString: connectionString(),
      ssl: { rejectUnauthorized: true },
      max: 8,
      idleTimeoutMillis: 30_000,
    });
  }
  return globalThis.__winkPool;
}

type Params = readonly unknown[];

/** All rows. */
export async function q<T>(text: string, params: Params = []): Promise<T[]> {
  const result = await pool().query(text, params as unknown[]);
  return result.rows as T[];
}

/** The first row, or undefined. */
export async function one<T>(text: string, params: Params = []): Promise<T | undefined> {
  return (await q<T>(text, params))[0];
}

/** Rows affected. */
export async function run(text: string, params: Params = []): Promise<number> {
  const result = await pool().query(text, params as unknown[]);
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
