"use client";

import { useEffect, useState } from "react";
import { api, formatBytes } from "@/lib/client";

type Meta = { id: string; name: string; mime: string; size: number; downloadUrl: string; viewUrl: string; officeViewerUrl: string | null };

/**
 * A document in the browser: download it, or read it here. PDFs and images
 * render in the page; Office files go through Microsoft's viewer.
 */
export function DocumentDialog({ fileId, onClose }: { fileId: string; onClose: () => void }) {
  const [meta, setMeta] = useState<Meta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [viewing, setViewing] = useState(false);

  useEffect(() => {
    api<Meta>(`/api/files/${fileId}`)
      .then((m) => {
        setMeta(m);
        // Something the browser can show opens at once, as a chat app does; only other files ask what to do.
        if (m.mime === "application/pdf" || m.mime.startsWith("image/") || m.mime.startsWith("text/") || m.officeViewerUrl) setViewing(true);
      })
      .catch((e) => setError(e.message));
  }, [fileId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const canView = meta ? meta.mime === "application/pdf" || meta.mime.startsWith("image/") || meta.mime.startsWith("text/") || Boolean(meta.officeViewerUrl) : false;
  const viewSrc = meta?.officeViewerUrl ?? meta?.viewUrl ?? "";

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-night/80 p-3 sm:items-center" onClick={onClose}>
      <div
        className={`card w-full ${viewing ? "flex h-[92vh] max-w-5xl flex-col p-3" : "max-w-md"}`}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal
      >
        {error && <p className="error">{error}</p>}
        {!meta && !error && <p className="text-sm text-snow-faint">Loading…</p>}
        {meta && !viewing && (
          <>
            <p className="truncate font-semibold">{meta.name}</p>
            <p className="mt-1 text-xs text-snow-faint">{formatBytes(meta.size)}</p>
            <div className="mt-5 flex flex-col gap-2 sm:flex-row">
              <a href={meta.downloadUrl} className="btn-gold flex-1" download={meta.name}>
                Download
              </a>
              {canView && (
                <button className="btn-ghost flex-1" onClick={() => setViewing(true)}>
                  View
                </button>
              )}
              <button className="btn-ghost" onClick={onClose}>
                Close
              </button>
            </div>
          </>
        )}
        {meta && viewing && (
          <>
            <div className="mb-2 flex items-center gap-2">
              <p className="min-w-0 flex-1 truncate text-sm font-semibold">{meta.name}</p>
              <a href={meta.downloadUrl} className="btn-ghost px-3 py-1.5 text-xs" download={meta.name}>
                Download
              </a>
              <button className="btn-ghost px-3 py-1.5 text-xs" onClick={onClose}>
                Close
              </button>
            </div>
            <iframe src={viewSrc} title={meta.name} className="min-h-0 flex-1 rounded-xl bg-white" />
          </>
        )}
      </div>
    </div>
  );
}
