import { parseFormatting, type Piece } from "@/lib/formatting";

const CLASS = { mono: "font-mono", bold: "font-bold", italic: "italic", underline: "underline", strike: "line-through" } as const;

function render(pieces: Piece[], key = ""): React.ReactNode[] {
  return pieces.map((p, i) =>
    typeof p === "string" ? p : (
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
