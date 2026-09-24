"use client";

import { useEffect, useRef, useState } from "react";
import { Glyph } from "./Glyph";

export type MenuItem = { label: string; icon: React.ReactNode; tone: "gold" | "soft" | "danger"; onClick: () => void; hidden?: boolean };

const TONE = { gold: "text-gold", soft: "text-snow-soft", danger: "text-danger" } as const;

/** The ⋮ button and its little menu: icon and text in the item's colour. A click outside or Escape closes it. */
export function WeekendMenu({ items, label = "More" }: { items: MenuItem[]; label?: string }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => box.current && !box.current.contains(e.target as Node) && setOpen(false);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", onDown);
    window.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={box} className="relative">
      <button className="btn-icon" aria-label={label} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Glyph name="more" />
      </button>
      {open && (
        <ul role="menu" className="absolute right-0 top-full z-20 mt-1 min-w-[12rem] rounded-xl border border-night-line bg-night-panel py-1 shadow-card">
          {items
            .filter((i) => !i.hidden)
            .map((i) => (
              <li key={i.label} role="none">
                <button
                  role="menuitem"
                  className={`flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm font-medium transition-colors hover:bg-snow/5 ${TONE[i.tone]}`}
                  onClick={() => {
                    setOpen(false);
                    i.onClick();
                  }}
                >
                  <span className="shrink-0">{i.icon}</span>
                  {i.label}
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
