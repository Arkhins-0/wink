"use client";

import { useEffect, useRef, useState } from "react";
import { api, uploadFile } from "@/lib/client";
import { Icon } from "./Icon";

const DOCS = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.txt";

export type Draft = { body: string; fileId: string | null; urgent: boolean };

/**
 * The message box, the way a chat app does it: the clip and send inside
 * the field; with nothing to send, a mic that records a voice note; a
 * small menu on send for the urgent send that also goes out by email.
 * The clip offers a photo, a document, an audio file, or your location.
 */
export function MessageComposer({
  send,
  placeholder = "Message",
  urgentOption = true,
  submitLabel = "Send",
}: {
  send: (draft: Draft) => Promise<void>;
  placeholder?: string;
  urgentOption?: boolean;
  submitLabel?: string;
}) {
  const [body, setBody] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [menu, setMenu] = useState<"none" | "attach" | "send">("none");
  const [recording, setRecording] = useState<{ recorder: MediaRecorder; started: number } | null>(null);
  const [seconds, setSeconds] = useState(0);
  const [locating, setLocating] = useState(false);
  const photoInput = useRef<HTMLInputElement>(null);
  const docInput = useRef<HTMLInputElement>(null);
  const audioInput = useRef<HTMLInputElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const discard = useRef(false);

  useEffect(() => {
    if (!recording) return setSeconds(0);
    const t = setInterval(() => setSeconds(Math.floor((Date.now() - recording.started) / 1000)), 500);
    return () => clearInterval(t);
  }, [recording]);

  useEffect(() => {
    const el = textarea.current;
    if (!el) return;
    el.style.height = "0px";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [body]);

  const doSend = async (urgent: boolean, attachment: File | null = file, text: string = body) => {
    if (busy || (!text.trim() && !attachment)) return;
    setBusy(true);
    setError(null);
    setMenu("none");
    try {
      let fileId: string | null = null;
      if (attachment) {
        setProgress(0);
        fileId = await uploadFile(attachment, setProgress);
      }
      await send({ body: text.trim(), fileId, urgent });
      setBody("");
      setFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send.");
    } finally {
      setBusy(false);
      setProgress(null);
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mime = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4", "audio/ogg"].find((t) => MediaRecorder.isTypeSupported(t)) ?? "";
      const recorder = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      const chunks: Blob[] = [];
      recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        if (discard.current) return;
        const type = recorder.mimeType.split(";")[0] || "audio/webm";
        const ext = type.includes("mp4") ? "m4a" : type.includes("ogg") ? "ogg" : "webm";
        const secs = Math.round((Date.now() - started) / 1000);
        if (secs < 1) return;
        const note = new File(chunks, `Voice note ${Math.floor(secs / 60)}-${String(secs % 60).padStart(2, "0")}.${ext}`, { type });
        doSend(false, note, "");
      };
      const started = Date.now();
      discard.current = false;
      recorder.start();
      setRecording({ recorder, started });
    } catch (err) {
      const name = (err as { name?: string }).name;
      setError(
        name === "NotAllowedError"
          ? "Microphone access is blocked. Allow it in the site settings (the icon left of the address, or the app info for the installed app) and try again."
          : name === "NotFoundError"
            ? "No microphone was found on this device."
            : "The microphone could not be opened. Try again.",
      );
    }
  };
  const stopRecording = (send: boolean) => {
    discard.current = !send;
    recording?.recorder.stop();
    setRecording(null);
  };

  const shareLocation = () => {
    setMenu("none");
    if (!navigator.geolocation) return setError("This browser cannot share your location.");
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        doSend(false, null, `📍 My location\nhttps://maps.google.com/?q=${pos.coords.latitude.toFixed(6)},${pos.coords.longitude.toFixed(6)}`);
      },
      () => {
        setLocating(false);
        setError("Could not get your location. Allow it in the browser and try again.");
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  const canSend = !busy && (body.trim().length > 0 || file !== null);

  return (
    <div className="relative rounded-3xl border border-night-line bg-night-panel px-2 py-1.5">
      {error && <p className="error mx-1 mb-2">{error}</p>}
      {file && (
        <div className="mx-2 mb-1 flex items-center gap-2 text-xs text-snow-soft">
          <Icon name={file.type.startsWith("image/") ? "gallery" : file.type.startsWith("audio/") ? "audio" : "document"} className="h-4 w-4 text-gold" />
          <span className="truncate">{file.name}</span>
          {progress !== null && <span className="text-snow-faint">{Math.round(progress * 100)}%</span>}
          <button type="button" className="ml-auto text-snow-faint hover:text-snow" onClick={() => setFile(null)} disabled={busy}>
            Remove
          </button>
        </div>
      )}
      <div className="flex items-end gap-1">
        {recording ? (
          <div className="flex min-h-[44px] flex-1 items-center gap-2 px-3 text-sm">
            <span className="h-2.5 w-2.5 animate-pulse rounded-full bg-danger" />
            Recording {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")}
            <button type="button" className="btn-icon ml-auto text-danger hover:text-danger" title="Delete recording" aria-label="Delete recording" onClick={() => stopRecording(false)}>
              <Icon name="trash" className="h-5 w-5" />
            </button>
          </div>
        ) : (
          <>
            <textarea
              ref={textarea}
              rows={1}
              className="min-h-[44px] flex-1 resize-none bg-transparent px-3 py-2.5 text-sm text-snow placeholder:text-snow-faint focus:outline-none"
              placeholder={placeholder}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  doSend(false);
                }
              }}
              maxLength={5000}
              disabled={busy}
            />
            <button type="button" className="btn-icon" title="Attach" aria-label="Attach" disabled={busy} onClick={() => setMenu(menu === "attach" ? "none" : "attach")}>
              <Icon name="clip" className="h-5 w-5" />
            </button>
          </>
        )}
        {busy ? (
          <span className="btn-icon">
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-gold border-t-transparent" />
          </span>
        ) : canSend ? (
          <span className="flex items-end">
            <button type="button" className="btn-icon text-gold hover:text-gold" title={submitLabel} aria-label={submitLabel} onClick={() => doSend(false)}>
              <Icon name="send" className="h-5 w-5" />
            </button>
            {urgentOption && (
              <button type="button" className="btn-icon -ml-2 h-9 w-6 text-snow-faint" title="More ways to send" aria-label="More ways to send" onClick={() => setMenu(menu === "send" ? "none" : "send")}>
                ▾
              </button>
            )}
          </span>
        ) : recording ? (
          <button type="button" className="btn-icon text-gold hover:text-gold" title="Send voice note" aria-label="Send voice note" onClick={() => stopRecording(true)}>
            <Icon name="send" className="h-5 w-5" />
          </button>
        ) : (
          <button type="button" className="btn-icon text-gold hover:text-gold" title="Record a voice note" aria-label="Record a voice note" onClick={startRecording}>
            <Icon name="mic" className="h-5 w-5" />
          </button>
        )}
      </div>

      {menu === "send" && (
        <div className="absolute bottom-full right-2 mb-2 w-64 rounded-xl border border-night-line bg-night-panel p-1 shadow-card">
          <button type="button" className="row w-full text-left text-sm" onClick={() => doSend(false)}>
            <Icon name="send" className="h-4 w-4 text-gold" /> {submitLabel}
          </button>
          <button type="button" className="row w-full text-left text-sm" onClick={() => doSend(true)}>
            <Icon name="mail" className="h-4 w-4 text-danger" /> Send as urgent · also by email
          </button>
        </div>
      )}
      {menu === "attach" && (
        <div className="absolute bottom-full right-2 mb-2 flex gap-2 rounded-2xl border border-night-line bg-night-panel p-3 shadow-card">
          {(
            [
              ["gallery", "Photo", () => photoInput.current?.click()],
              ["document", "Document", () => docInput.current?.click()],
              ["audio", "Audio", () => audioInput.current?.click()],
              ["location", "Location", shareLocation],
            ] as const
          ).map(([icon, label, act]) => (
            <button key={label} type="button" className="flex w-16 flex-col items-center gap-1.5 text-xs text-snow-soft" onClick={act} disabled={locating}>
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gold text-night">
                {icon === "location" && locating ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-night border-t-transparent" /> : <Icon name={icon} className="h-6 w-6" />}
              </span>
              {label}
            </button>
          ))}
        </div>
      )}
      <input ref={photoInput} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setMenu("none"); e.target.value = ""; }} />
      <input ref={docInput} type="file" accept={DOCS} className="hidden" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setMenu("none"); e.target.value = ""; }} />
      <input ref={audioInput} type="file" accept="audio/*" className="hidden" onChange={(e) => { setFile(e.target.files?.[0] ?? null); setMenu("none"); e.target.value = ""; }} />
    </div>
  );
}

export const post = (path: string, draft: Draft) => api(path, { method: "POST", json: draft });
