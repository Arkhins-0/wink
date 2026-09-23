import "server-only";

import { createHash, randomBytes, randomInt } from "node:crypto";

/** A URL-safe random token, 256 bits. */
export const randomToken = (): string => randomBytes(32).toString("base64url");

/** What gets stored for a token: never the token itself. */
export const hashToken = (token: string): string => createHash("sha256").update(token).digest("hex");

// Uppercase letters and digits, minus the ones that read alike when typed
// from a badge: O/0, I/1, and B/8 look too close on a bad print.
const CODE_ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ2345679";

/** The 8-character account code, as `XXXX-XXXX`. */
export function verifyCode(): string {
  let out = "";
  for (let i = 0; i < 8; i++) out += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return `${out.slice(0, 4)}-${out.slice(4)}`;
}

/** What a person typed, normalised to the stored form: uppercase, dashed. */
export function normaliseCode(input: string): string | null {
  const raw = input.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (raw.length !== 8) return null;
  return `${raw.slice(0, 4)}-${raw.slice(4)}`;
}
