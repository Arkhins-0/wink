/*
 * Text formatting, the way WhatsApp writes it (the app reads the same, in Formatting.kt):
 *   *bold*   _italic_   ~strikethrough~   ```monospace```   __underline__
 * A marker counts only at a word's edge, with no space just inside it, on one line. Markers can nest.
 * Pure, so the server (plain text for notifications) and the browser (the formatted message) share it.
 */

export type Mark = "mono" | "underline" | "bold" | "italic" | "strike";
export type Piece = string | { mark: Mark; children: Piece[] };

const MARKS: { token: string; mark: Mark }[] = [
  { token: "```", mark: "mono" },
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

/** As [parseFormatting], but each marked group keeps its marker: what a text box shows while typing. */
export type LivePiece = string | { mark: Mark; token: string; children: LivePiece[] };

export function parseLive(text: string): LivePiece[] {
  const out: LivePiece[] = [];
  let plain = "";
  let i = 0;
  while (i < text.length) {
    const found = edge(text[i - 1]) ? MARKS.find((m) => text.startsWith(m.token, i)) : undefined;
    const close = found ? closing(text, i, found.token) : -1;
    if (found && close > 0) {
      if (plain) out.push(plain);
      plain = "";
      out.push({ mark: found.mark, token: found.token, children: parseLive(text.slice(i + found.token.length, close)) });
      i = close + found.token.length;
    } else {
      plain += text[i];
      i++;
    }
  }
  if (plain) out.push(plain);
  return out;
}

export const TOKEN: Record<Mark, string> = { bold: "*", italic: "_", underline: "__", strike: "~", mono: "```" };

/**
 * Put a marker round the selection, or take it off when it is already there (WhatsApp's menu). Spaces at the
 * selection's edges stay outside, since a marker only counts against a word. Gives the new text and selection.
 */
export function toggleMark(text: string, start: number, end: number, mark: Mark): { text: string; start: number; end: number } {
  const token = TOKEN[mark];
  let s = start;
  let e = end;
  while (s < e && /\s/.test(text[s])) s++;
  while (e > s && /\s/.test(text[e - 1])) e--;
  if (s === e) return { text, start, end };
  // Already marked, markers just outside the selection…
  if (text.slice(s - token.length, s) === token && text.slice(e, e + token.length) === token) {
    return { text: text.slice(0, s - token.length) + text.slice(s, e) + text.slice(e + token.length), start: s - token.length, end: e - token.length };
  }
  // …or just inside it.
  if (e - s > 2 * token.length && text.startsWith(token, s) && text.slice(e - token.length, e) === token) {
    return { text: text.slice(0, s) + text.slice(s + token.length, e - token.length) + text.slice(e), start: s, end: e - 2 * token.length };
  }
  return { text: text.slice(0, s) + token + text.slice(s, e) + token + text.slice(e), start: s + token.length, end: e + token.length };
}

/** The words alone, markers gone: notifications, previews, search. */
export function plainText(text: string): string {
  const flat = (pieces: Piece[]): string => pieces.map((p) => (typeof p === "string" ? p : flat(p.children))).join("");
  return flat(parseFormatting(text));
}
