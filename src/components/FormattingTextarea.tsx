"use client";

import { forwardRef, useImperativeHandle, useRef, useState } from "react";
import { parseLive, toggleMark, type LivePiece, type Mark } from "@/lib/formatting";

/*
 * The message box's text field, with WhatsApp's formatting: what is typed shows styled as it is typed (markers
 * dimmed), and selecting words brings a small menu (Bold, Italic, Underline, Strikethrough, Monospace); Ctrl+B, I, U
 * and Ctrl+Shift+X, M do the same. The app has the same module (FormattedTextField.kt).
 *
 * How: the textarea's own text is invisible (only its caret and selection show) over a copy that draws the styles.
 * The copy keeps every letter's width (bold is a thin outline, italic is the slanted face the browser makes, and
 * monospace is a tint), so the caret always sits where the letters are.
 */

const LOOK: Record<Mark, string> = {
  bold: "[-webkit-text-stroke:0.45px_currentColor]",
  italic: "italic",
  underline: "underline",
  strike: "line-through",
  mono: "rounded bg-snow/10",
};

function draw(pieces: LivePiece[], key = ""): React.ReactNode[] {
  return pieces.map((p, i) =>
    typeof p === "string" ? (
      p
    ) : (
      <span key={`${key}${i}`} className={LOOK[p.mark]}>
        <span className="text-snow-faint">{p.token}</span>
        {draw(p.children, `${key}${i}.`)}
        <span className="text-snow-faint">{p.token}</span>
      </span>
    ),
  );
}

const MENU: { mark: Mark; label: string; look: string; keys: string }[] = [
  { mark: "bold", label: "B", look: "font-bold", keys: "Bold (Ctrl+B)" },
  { mark: "italic", label: "I", look: "italic", keys: "Italic (Ctrl+I)" },
  { mark: "underline", label: "U", look: "underline", keys: "Underline (Ctrl+U)" },
  { mark: "strike", label: "S", look: "line-through", keys: "Strikethrough (Ctrl+Shift+X)" },
  { mark: "mono", label: "</>", look: "font-mono", keys: "Monospace (Ctrl+Shift+M)" },
];

type Props = Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange"> & {
  value: string;
  onValueChange: (text: string) => void;
};

export const FormattingTextarea = forwardRef<HTMLTextAreaElement, Props>(function FormattingTextarea(
  { value, onValueChange, className = "", onKeyDown, onScroll, ...rest },
  ref,
) {
  const area = useRef<HTMLTextAreaElement>(null);
  const copy = useRef<HTMLDivElement>(null);
  const [picked, setPicked] = useState(false);
  useImperativeHandle(ref, () => area.current as HTMLTextAreaElement);

  const apply = (mark: Mark) => {
    const el = area.current;
    if (!el) return;
    const r = toggleMark(value, el.selectionStart, el.selectionEnd, mark);
    onValueChange(r.text);
    // After React has put the new text in, the same words stay selected.
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(r.start, r.end);
    });
  };

  const noteSelection = () => {
    const el = area.current;
    setPicked(Boolean(el && el.selectionStart !== el.selectionEnd));
  };

  // Padding, font and wrapping must be the same on both layers.
  const shared = `whitespace-pre-wrap break-words ${className}`;
  return (
    <div className="relative min-w-0 flex-1">
      {picked && (
        <div
          className="absolute bottom-full left-2 z-10 mb-2 flex gap-0.5 rounded-xl border border-night-line bg-night-panel p-1 shadow-card"
          // Clicking the menu must not take the selection away.
          onMouseDown={(e) => e.preventDefault()}
        >
          {MENU.map((m) => (
            <button key={m.mark} type="button" title={m.keys} aria-label={m.keys} className={`h-8 min-w-8 rounded-lg px-2 text-sm text-snow hover:bg-night ${m.look}`} onClick={() => apply(m.mark)}>
              {m.label}
            </button>
          ))}
        </div>
      )}
      <div ref={copy} aria-hidden className={`pointer-events-none absolute inset-0 overflow-hidden text-snow ${shared}`}>
        {draw(parseLive(value))}
        {/* A last line break needs something after it to take up its line. */}
        {"​"}
      </div>
      <textarea
        ref={area}
        {...rest}
        value={value}
        className={`relative block w-full bg-transparent text-transparent caret-snow selection:bg-gold/30 selection:text-transparent ${shared}`}
        onChange={(e) => onValueChange(e.target.value)}
        onSelect={noteSelection}
        onBlur={() => setPicked(false)}
        onScroll={(e) => {
          if (copy.current) copy.current.scrollTop = e.currentTarget.scrollTop;
          onScroll?.(e);
        }}
        onKeyDown={(e) => {
          if (e.ctrlKey || e.metaKey) {
            const k = e.key.toLowerCase();
            const mark: Mark | null = e.shiftKey ? (k === "x" ? "strike" : k === "m" ? "mono" : null) : k === "b" ? "bold" : k === "i" ? "italic" : k === "u" ? "underline" : null;
            if (mark) {
              e.preventDefault();
              return apply(mark);
            }
          }
          onKeyDown?.(e);
        }}
      />
    </div>
  );
});
