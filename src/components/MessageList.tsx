"use client";

import Link from "next/link";
import { useState } from "react";
import type { MessageOut } from "@/lib/messages";
import { formatBytes, timeAgo } from "@/lib/client";
import { Avatar } from "./Avatar";
import { DocumentDialog } from "./DocumentDialog";

/** A document line inside a message; tapping it opens the download/view dialog. */
export function Attachment({ file }: { file: NonNullable<MessageOut["file"]> }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        className="mt-2 flex w-full items-center gap-3 rounded-xl border border-night-line bg-night px-3 py-2 text-left hover:border-gold/50"
        onClick={() => setOpen(true)}
      >
        <span className="rounded-lg bg-gold/15 px-2 py-1 text-[10px] font-bold uppercase text-gold">
          {file.name.split(".").pop()?.slice(0, 4) || "doc"}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm">{file.name}</span>
          <span className="block text-xs text-snow-faint">{formatBytes(file.size)}</span>
        </span>
      </button>
      {open && <DocumentDialog fileId={file.id} onClose={() => setOpen(false)} />}
    </>
  );
}

/** One message as it appears in the inbox, a chat or a channel. */
export function MessageItem({ m, showSender = true, highlight = false }: { m: MessageOut; showSender?: boolean; highlight?: boolean }) {
  const unread = !m.readAt && !m.mine;
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
            <span className="ml-auto shrink-0 text-xs text-snow-faint">{timeAgo(m.createdAt)}</span>
          </div>
          {m.body && <p className="mt-1 whitespace-pre-wrap break-words text-sm text-snow-soft">{m.body}</p>}
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
