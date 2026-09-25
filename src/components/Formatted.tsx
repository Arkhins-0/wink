import { parseFormatting, type Piece } from "@/lib/formatting";

const CLASS = { mono: "font-mono", bold: "font-bold", italic: "italic", underline: "underline", strike: "line-through" } as const;

const URL_RE = /\bhttps?:\/\/[^\s<>"']+/gi;

/** Plain words with every link made one (underlined, opening in a new tab), the way chat apps show them. */
function linked(text: string, key: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  let from = 0;
  for (const m of text.matchAll(URL_RE)) {
    const url = m[0].replace(/[.,;:!?)\]}'"]+$/, "");
    const at = m.index ?? 0;
    parts.push(text.slice(from, at));
    parts.push(
      <a key={`${key}l${at}`} href={url} target="_blank" rel="noreferrer noopener" className="underline underline-offset-2" onClick={(e) => e.stopPropagation()}>
        {url}
      </a>,
    );
    from = at + url.length;
  }
  if (from === 0) return text;
  parts.push(text.slice(from));
  return parts;
}

function render(pieces: Piece[], key = ""): React.ReactNode[] {
  return pieces.map((p, i) =>
    typeof p === "string" ? linked(p, `${key}${i}`) : (
      <span key={`${key}${i}`} className={CLASS[p.mark]}>
        {render(p.children, `${key}${i}.`)}
      </span>
    ),
  );
}

/** A message's words with *bold*, _italic_, __underline__, ~strikethrough~ and ```monospace``` shown, markers hidden. */
export function Formatted({ text }: { text: string }) {
  return <>{render(parseFormatting(text))}</>;
}
