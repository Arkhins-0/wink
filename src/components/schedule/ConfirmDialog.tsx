"use client";

import { useEffect } from "react";

/**
 * A centred question before something big happens: a title, a coloured
 * first line saying how big, what it means, and one button to go ahead.
 * Escape or a tap outside backs out.
 */
export function ConfirmDialog({
  title,
  lead,
  body,
  note,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}: {
  title: string;
  lead?: string;
  body?: React.ReactNode;
  note?: string;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !busy && onCancel();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [busy, onCancel]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-night/80 p-4" onClick={() => !busy && onCancel()}>
      <div className="card w-full max-w-md space-y-3" onClick={(e) => e.stopPropagation()} role="alertdialog" aria-modal aria-label={title}>
        <p className="text-lg font-semibold leading-snug">{title}</p>
        {lead && <p className={`text-sm font-semibold ${danger ? "text-danger" : "text-gold"}`}>{lead}</p>}
        {body && <div className="text-sm text-snow-soft">{body}</div>}
        {note && <p className="text-xs text-snow-faint">{note}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button className={`${danger ? "btn-danger" : "btn-gold"} px-4 py-1.5 text-xs`} onClick={onConfirm} disabled={busy} autoFocus>
            {busy ? "Working…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
