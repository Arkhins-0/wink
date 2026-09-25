"use client";

import Link from "next/link";
import { useState } from "react";
import type { MessageOut, ReplyRef } from "@/lib/messages";
import { formatBytes, timeAgo } from "@/lib/client";
import { Avatar } from "./Avatar";
import { DocumentDialog } from "./DocumentDialog";
import { Icon } from "./Icon";
import { InviteCard } from "./chat/InviteCard";

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
  // Sent through "Document": a document card, whatever its type.
  if (!file.document && file.mime.startsWith("audio/")) {
    return (
      <div className={`mt-2 rounded-xl border px-3 py-2 ${onDark ? "border-night-line bg-night" : "border-night/20 bg-night/10"}`}>
        <audio controls preload="none" src={`/api/files/${file.id}/content?inline=1`} className="h-9 w-full max-w-xs" />
        <p className={`mt-1 truncate text-xs ${onDark ? "text-snow-faint" : "text-night/60"}`}>{file.name}</p>
      </div>
    );
  }
  if (!file.document && file.mime.startsWith("image/")) {
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

const TICK_LABEL = { sent: "Sent", delivered: "Delivered", read: "Read" } as const;

/** One tick sent, two delivered, three read (the read ones in blue). */
export function Ticks({ status }: { status: NonNullable<MessageOut["status"]> }) {
  const n = status === "read" ? 3 : status === "delivered" ? 2 : 1;
  return (
    <svg
      viewBox={`0 0 ${10 + (n - 1) * 5} 10`}
      className={`ml-1 inline-block h-2.5 align-[-1px] ${status === "read" ? "text-sky-700" : "text-night/60"}`}
      style={{ width: `${(10 + (n - 1) * 5) * 1.1}px` }}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      role="img"
      aria-label={TICK_LABEL[status]}
    >
      <title>{TICK_LABEL[status]}</title>
      {Array.from({ length: n }, (_, i) => (
        <path key={i} d={`M${1 + i * 5} 5.5l2.5 2.5L${9 + i * 5} 2`} />
      ))}
    </svg>
  );
}

/** One line saying what a message was: its text, or what it carried. */
export function snippet(r: { body: string; fileName: string | null; fileMime: string | null; deleted: boolean }): string {
  if (r.deleted) return "This message was deleted";
  if (locationIn(r.body)) return "📍 Location";
  if (r.body.trim()) return r.body.trim();
  if (r.fileMime?.startsWith("image/")) return "📷 Photo";
  if (r.fileMime?.startsWith("audio/")) return "🎤 Voice note";
  return r.fileName ? `📄 ${r.fileName}` : "";
}

/** A message this one answers, quoted inside the bubble. Clicking it goes there. */
function Quote({ r, onDark, onClick }: { r: ReplyRef; onDark: boolean; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onClick?.();
      }}
      className={`mb-1 block w-full rounded-lg border-l-4 px-2 py-1 text-left ${onDark ? "border-gold bg-night" : "border-night/50 bg-night/10"}`}
    >
      <span className={`block text-xs font-semibold ${onDark ? "text-gold" : "text-night"}`}>{r.mine ? "You" : r.senderName}</span>
      <span className={`line-clamp-2 block text-xs ${r.deleted ? "italic " : ""}${onDark ? "text-snow-soft" : "text-night/70"}`}>{snippet(r)}</span>
    </button>
  );
}

/** Message text with every match of the search lit up. */
function Highlighted({ text, needle, mine }: { text: string; needle: string | null; mine: boolean }) {
  if (!needle) return <>{text}</>;
  const parts: React.ReactNode[] = [];
  const lower = text.toLowerCase();
  const n = needle.toLowerCase();
  let from = 0;
  for (let at = lower.indexOf(n); at >= 0; at = lower.indexOf(n, from)) {
    parts.push(text.slice(from, at));
    parts.push(
      <mark key={at} className={`rounded-sm ${mine ? "bg-night/25 text-night" : "bg-gold/45 text-snow"}`}>
        {text.slice(at, at + n.length)}
      </mark>,
    );
    from = at + n.length;
  }
  parts.push(text.slice(from));
  return <>{parts}</>;
}

/**
 * A chat bubble: gold on the right for what you sent, dark on the left for
 * what came in. The row around it takes the selection gestures (`press`),
 * a quoted reply jumps to the original, and a jumped-to bubble flashes.
 * In a group, what others said carries their name in gold.
 */
export function Bubble({
  m,
  quote = m.replyTo,
  onQuote,
  flash = false,
  selected = false,
  senderName = null,
  highlight = null,
  onInvite,
  press,
  children,
}: {
  m: MessageOut;
  quote?: ReplyRef | null;
  onQuote?: (id: string) => void;
  flash?: boolean;
  selected?: boolean;
  senderName?: string | null;
  /** The search, when this message matches it. */
  highlight?: string | null;
  /** Answer the group invitation this message carries. */
  onInvite?: (accept: boolean) => Promise<void>;
  press?: React.HTMLAttributes<HTMLDivElement>;
  children?: React.ReactNode;
}) {
  const loc = locationIn(m.body);
  return (
    <div
      id={`m-${m.id}`}
      {...press}
      className={`-mx-2 rounded-xl px-2 py-0.5 transition-colors duration-700 [-webkit-touch-callout:none] ${selected ? "bg-gold/15 duration-150" : flash ? "bg-gold/20" : "bg-transparent"}`}
    >
      <div className={`flex ${m.mine ? "justify-end" : "justify-start"}`}>
        <div className={m.mine ? "bubble-mine" : "bubble-theirs"}>
          {m.deleted ? (
            <p className={`italic ${m.mine ? "text-night/70" : "text-snow-faint"}`}>This message was deleted</p>
          ) : (
            <>
              {senderName && <p className="mb-0.5 truncate text-xs font-semibold text-gold">{senderName}</p>}
              {m.forwarded && <span className={`mb-1 block text-[11px] italic ${m.mine ? "text-night/60" : "text-snow-faint"}`}>↪ Forwarded</span>}
              {quote && <Quote r={quote} onDark={!m.mine} onClick={() => onQuote?.(quote.id)} />}
              {m.groupInvite ? (
                <InviteCard inv={m.groupInvite} mine={m.mine} onAnswer={onInvite} />
              ) : loc ? (
                <LocationCard lat={loc.lat} lng={loc.lng} onDark={!m.mine} />
              ) : (
                m.body && (
                  <p className="whitespace-pre-wrap break-words">
                    <Highlighted text={m.body} needle={highlight} mine={m.mine} />
                  </p>
                )
              )}
              {m.file && <Attachment file={m.file} onDark={!m.mine} />}
            </>
          )}
          <p className={`mt-1 flex items-center justify-end text-[10px] ${m.mine ? "text-night/60" : "text-snow-faint"}`}>
            {m.editedAt && !m.deleted ? "edited · " : ""}
            {timeOnly(m.createdAt)}
            {m.status && !m.deleted && <Ticks status={m.status} />}
            {/* Marked urgent: it also went out by email. */}
            {m.urgent && !m.deleted && (
              <span title="Urgent · also sent by email" className="ml-1 text-danger">
                <Icon name="mail" className="h-3 w-3" />
              </span>
            )}
          </p>
        </div>
      </div>
      {children}
    </div>
  );
}
