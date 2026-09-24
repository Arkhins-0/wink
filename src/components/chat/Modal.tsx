"use client";

import { useEffect } from "react";

/** A card over the page, from the bottom on a phone; Escape or a click outside closes it (unless it must stay). */
export function Modal({ onClose, children, className = "max-w-md" }: { onClose?: () => void; children: React.ReactNode; className?: string }) {
  useEffect(() => {
    if (!onClose) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-night/80 p-3 sm:items-center" onClick={onClose}>
      <div className={`card w-full ${className}`} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal>
        {children}
      </div>
    </div>
  );
}
