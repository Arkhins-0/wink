"use client";

import { useEffect } from "react";

/** A sheet from the bottom on a phone, a centred box on a laptop. Escape or a tap outside closes it. */
export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-night/80 p-3 sm:items-center" onClick={onClose}>
      <div
        className="card flex max-h-[88vh] w-full max-w-md flex-col gap-3 p-4"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal
        aria-label={title}
      >
        <p className="font-semibold">{title}</p>
        {children}
      </div>
    </div>
  );
}
