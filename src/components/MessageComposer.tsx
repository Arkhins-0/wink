"use client";

import { useRef, useState } from "react";
import { api, uploadFile } from "@/lib/client";

const ACCEPT = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.txt,image/jpeg,image/png,image/webp,audio/*";

/**
 * Text, an optional document, an urgent switch, send. Used by channels,
 * chats and the broadcast form; `send` does the actual POST.
 */
export function MessageComposer({
  send,
  placeholder = "Write a message",
  urgentOption = true,
  submitLabel = "Send",
}: {
  send: (draft: { body: string; fileId: string | null; urgent: boolean }) => Promise<void>;
  placeholder?: string;
  urgentOption?: boolean;
  submitLabel?: string;
}) {
  const [body, setBody] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [urgent, setUrgent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy || (!body.trim() && !file)) return;
    setBusy(true);
    setError(null);
    try {
      let fileId: string | null = null;
      if (file) {
        setProgress(0);
        fileId = await uploadFile(file, setProgress);
      }
      await send({ body: body.trim(), fileId, urgent });
      setBody("");
      setFile(null);
      setUrgent(false);
      if (input.current) input.current.value = "";
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send.");
    } finally {
      setBusy(false);
      setProgress(null);
    }
  };

  return (
    <form onSubmit={submit} className="card p-3">
      {error && <p className="error mb-2">{error}</p>}
      <textarea
        className="input min-h-[72px] resize-y"
        placeholder={placeholder}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        maxLength={5000}
      />
      {file && (
        <div className="mt-2 flex items-center gap-2 text-xs text-snow-soft">
          <span className="truncate">{file.name}</span>
          {progress !== null && <span className="text-snow-faint">{Math.round(progress * 100)}%</span>}
          <button type="button" className="ml-auto text-snow-faint hover:text-snow" onClick={() => setFile(null)} disabled={busy}>
            Remove
          </button>
        </div>
      )}
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <label className="btn-ghost cursor-pointer px-3 py-1.5 text-xs">
          Attach document
          <input
            ref={input}
            type="file"
            accept={ACCEPT}
            className="hidden"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            disabled={busy}
          />
        </label>
        {urgentOption && (
          <label className="flex cursor-pointer items-center gap-1.5 text-xs text-snow-soft">
            <input type="checkbox" checked={urgent} onChange={(e) => setUrgent(e.target.checked)} className="accent-gold" />
            Urgent (also emails)
          </label>
        )}
        <button className="btn-gold ml-auto px-4 py-1.5 text-xs" disabled={busy || (!body.trim() && !file)}>
          {busy ? "Sending…" : submitLabel}
        </button>
      </div>
    </form>
  );
}

export const post = (path: string, draft: { body: string; fileId: string | null; urgent: boolean }) =>
  api(path, { method: "POST", json: draft });
