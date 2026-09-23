"use client";

import Link from "next/link";
import { useState } from "react";
import type { MessageOut } from "@/lib/messages";
import { formatBytes, timeAgo } from "@/lib/client";
import { Avatar } from "./Avatar";
import { DocumentDialog } from "./DocumentDialog";
import { Icon } from "./Icon";

const MAPS = /https:\/\/maps\.google\.com\/\?q=(-?\d+\.\d+),(-?\d+\.\d+)/;

/** A location message, if the body is one. */
export function locationIn(body: string): { lat: number; lng: number } | null {
  const m = MAPS.exec(body);
  return m ? { lat: Number(m[1]), lng: Number(m[2]) } : null;
}

function LocationCard({ lat, lng, onDark = true }: { lat: number; lng: number; onDark?: boolean }) {
  return (
    <a
      href={`https://maps.google.com/?q=${lat},${lng}`}
      target="_blank"
      rel="noreferrer"
      className={`mt-1 flex items-center gap-3 rounded-xl border px-3 py-2 ${onDark ? "border-night-line bg-night text-snow" : "border-night/20 bg-night/10 text-night"}`}
    >
      <span className="rounded-lg bg-gold/15 p-2 text-gold">
        <Icon name="location" className="h-5 w-5" />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium">Location</span>
        <span className={`block text-xs ${onDark ? "text-snow-faint" : "text-night/60"}`}>
          {lat.toFixed(5)}, {lng.toFixed(5)} · open in Maps
        </span>
      </span>
    </a>
  );
}

const timeOnly = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit", hour12: true });

/** A document, picture or audio inside a message. */
export function Attachment({ file, onDark = true }: { file: NonNullable<MessageOut["file"]>; onDark?: boolean }) {
  const [open, setOpen] = useState(false);
  if (file.mime.startsWith("audio/")) {
    return (
      <div className={`mt-2 rounded-xl border px-3 py-2 ${onDark ? "border-night-line bg-night" : "border-night/20 bg-night/10"}`}>
        <audio controls preload="none" src={`/api/files/${file.id}/content?inline=1`} className="h-9 w-full max-w-xs" />
        <p className={`mt-1 truncate text-xs ${onDark ? "text-snow-faint" : "text-night/60"}`}>{file.name}</p>
      </div>
    );
  }
  if (file.mime.startsWith("image/")) {
    return (
      <>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={`/api/files/${file.id}/content?inline=1`}
          alt={file.name}
          className="mt-2 max-h-80 max-w-full cursor-zoom-in rounded-xl border border-night-line object-cover"
          onClick={() => setOpen(true)}
        />
        {open && <DocumentDialog fileId={file.id} onClose={() => setOpen(false)} />}
      </>
    );
  }
  return (
    <>
      <button
        className={`mt-2 flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left hover:border-gold/50 ${onDark ? "border-night-line bg-night" : "border-night/20 bg-night/10"}`}
        onClick={() => setOpen(true)}
      >
        <span className="rounded-lg bg-gold/15 px-2 py-1 text-[10px] font-bold uppercase text-gold">{file.name.split(".").pop()?.slice(0, 4) || "doc"}</span>
        <span className="min-w-0 flex-1">
          <span className={`block truncate text-sm ${onDark ? "" : "text-night"}`}>{file.name}</span>
          <span className={`block text-xs ${onDark ? "text-snow-faint" : "text-night/60"}`}>{formatBytes(file.size)}</span>
        </span>
      </button>
      {open && <DocumentDialog fileId={file.id} onClose={() => setOpen(false)} />}
    </>
  );
}

/** One message as it appears in the inbox or a channel. */
export function MessageItem({ m, showSender = true, highlight = false }: { m: MessageOut; showSender?: boolean; highlight?: boolean }) {
  const unread = !m.readAt && !m.mine;
  const loc = locationIn(m.body);
  return (
    <article
      id={`m-${m.id}`}
      className={`rounded-2xl border p-4 ${highlight ? "border-gold/60" : unread ? "border-gold/25 bg-night-panel" : "border-night-line bg-night-panel/60"}`}
    >
      <div className="flex items-start gap-3">
        {showSender && <Avatar src={m.sender?.photoUrl ?? null} name={m.sender?.name ?? "?"} size={36} />}
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline gap-2">
            {showSender && (
              <p className="min-w-0 truncate text-sm font-semibold">
                {m.mine ? "You" : m.sender?.name ?? "Wink"}
                {!m.mine && m.sender?.roleLabel && <span className="font-normal text-snow-faint"> · {m.sender.roleLabel}</span>}
              </p>
            )}
            {m.urgent && <span className="chip border-danger/40 bg-danger/10 px-2 py-0 text-[10px] text-danger">Urgent</span>}
            <span className="ml-auto shrink-0 text-xs text-snow-faint" title={new Date(m.createdAt).toLocaleString()}>
              {timeAgo(m.createdAt)}
            </span>
          </div>
          {loc ? <LocationCard lat={loc.lat} lng={loc.lng} /> : m.body && <p className="mt-1 whitespace-pre-wrap break-words text-sm text-snow-soft">{m.body}</p>}
          {m.file && <Attachment file={m.file} />}
          {m.kind !== "broadcast" && (
            <p className="mt-2 text-xs text-snow-faint">
              {m.kind === "direct" && m.conversationId && (
                <Link href={`/chats/${m.conversationId}`} className="hover:text-snow">
                  Private chat →
                </Link>
              )}
              {m.kind === "channel" && m.weekendId && (
                <Link href={`/w/${m.weekendId}`} className="hover:text-snow">
                  Race weekend channel →
                </Link>
              )}
            </p>
          )}
        </div>
      </div>
    </article>
  );
}

/** A chat bubble: gold on the right for what you sent, dark on the left for what came in. */
export function Bubble({ m }: { m: MessageOut }) {
  const loc = locationIn(m.body);
  return (
    <div className={`flex ${m.mine ? "justify-end" : "justify-start"}`}>
      <div className={m.mine ? "bubble-mine" : "bubble-theirs"}>
        {m.urgent && <span className={`chip mb-1 px-2 py-0 text-[10px] ${m.mine ? "border-night/30 bg-night/10 text-night" : "border-danger/40 bg-danger/10 text-danger"}`}>Urgent</span>}
        {loc ? <LocationCard lat={loc.lat} lng={loc.lng} onDark={!m.mine} /> : m.body && <p className="whitespace-pre-wrap break-words">{m.body}</p>}
        {m.file && <Attachment file={m.file} onDark={!m.mine} />}
        <p className={`mt-1 text-right text-[10px] ${m.mine ? "text-night/60" : "text-snow-faint"}`}>{timeOnly(m.createdAt)}</p>
      </div>
    </div>
  );
}
