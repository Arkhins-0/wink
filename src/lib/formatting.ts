/*
 * Text formatting, the way WhatsApp writes it (the app reads the same, in Formatting.kt):
 *   *bold*   _italic_   ~strikethrough~   __underline__
 * A marker counts only at a word's edge, with no space just inside it, on one line. Markers can nest.
 * Pure, so the server (plain text for notifications) and the browser (the formatted message) share it.
 */

export type Mark = "underline" | "bold" | "italic" | "strike";
export type Piece = string | { mark: Mark; children: Piece[] };

const MARKS: { token: string; mark: Mark }[] = [
  { token: "__", mark: "underline" },
  { token: "*", mark: "bold" },
  { token: "_", mark: "italic" },
  { token: "~", mark: "strike" },
];

const edge = (c: string | undefined) => c === undefined || !/[\p{L}\p{N}]/u.test(c);
const space = (c: string | undefined) => c !== undefined && /\s/.test(c);

function closing(text: string, open: number, token: string): number {
  const start = open + token.length;
  if (start >= text.length || space(text[start])) return -1;
  for (let j = start + 1; j <= text.length - token.length; j++) {
    if (text[j] === "\n") return -1;
    if (text.startsWith(token, j) && !space(text[j - 1]) && edge(text[j + token.length]) && !(token === "_" && text[j + 1] === "_")) return j;
  }
  return -1;
}

/** The text as plain pieces and marked groups. */
export function parseFormatting(text: string): Piece[] {
  const out: Piece[] = [];
  let plain = "";
  let i = 0;
  while (i < text.length) {
    const found = edge(text[i - 1]) ? MARKS.find((m) => text.startsWith(m.token, i)) : undefined;
    const close = found ? closing(text, i, found.token) : -1;
    if (found && close > 0) {
      if (plain) out.push(plain);
      plain = "";
      out.push({ mark: found.mark, children: parseFormatting(text.slice(i + found.token.length, close)) });
      i = close + found.token.length;
    } else {
      plain += text[i];
      i++;
    }
  }
  if (plain) out.push(plain);
  return out;
}

/** The words alone, markers gone: notifications, previews, search. */
export function plainText(text: string): string {
  const flat = (pieces: Piece[]): string => pieces.map((p) => (typeof p === "string" ? p : flat(p.children))).join("");
  return flat(parseFormatting(text));
}
