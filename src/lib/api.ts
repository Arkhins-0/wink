import "server-only";

import { NextResponse } from "next/server";
import { AuthError } from "./auth";
import { fail } from "./http";

/**
 * Wraps a route handler: an AuthError becomes its status, anything else a
 * logged 500 with a plain message. Routes stay about what they do.
 */
export function handle<Ctx>(fn: (request: Request, ctx: Ctx) => Promise<Response>) {
  return async (request: Request, ctx: Ctx): Promise<Response> => {
    try {
      return await fn(request, ctx);
    } catch (error) {
      if (error instanceof AuthError)
        return error.code
          ? NextResponse.json({ error: error.message, code: error.code }, { status: error.status })
          : fail(error.message, error.status);
      if (error instanceof SyntaxError) return fail("Bad JSON.", 400);
      console.error(`[api] ${request.method} ${new URL(request.url).pathname}`, error);
      return fail("Something went wrong.", 500);
    }
  };
}

/** The `[id]`-style params of a route, awaited (Next 15 makes them a promise). */
export type Params<K extends string> = { params: Promise<Record<K, string>> };

/** The request body as an object, or {} when there is none. */
export async function body<T extends object = Record<string, unknown>>(request: Request): Promise<T> {
  const text = await request.text();
  if (!text) return {} as T;
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object") throw new SyntaxError("not an object");
  return parsed as T;
}

export const str = (v: unknown, max = 2000): string => (typeof v === "string" ? v.trim().slice(0, max) : "");
export const bool = (v: unknown): boolean => v === true || v === "true" || v === 1 || v === "1";
export const strings = (v: unknown, max = 1000): string[] =>
  Array.isArray(v) ? v.filter((x): x is string => typeof x === "string").slice(0, max) : [];

export const isUuid = (v: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);

export { NextResponse };
