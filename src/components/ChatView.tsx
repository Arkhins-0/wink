"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut, ReplyRef } from "@/lib/messages";
import { Icon } from "./Icon";
import { MessageComposer, post, type Banner } from "./MessageComposer";
import { Bubble, snippet } from "./MessageList";

const dayOf = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });

/** Your own messages can be edited or deleted for 2 hours after sending (the server holds the same line). */
const EDIT_WINDOW_MS = 2 * 60 * 60 * 1000;
const changeable = (m: MessageOut) => m.mine && !m.deleted && Date.now() - Date.parse(m.createdAt) < EDIT_WINDOW_MS;

/** A message as a quote: what the composer shows while answering it. */
const asRef = (m: MessageOut): ReplyRef => ({
  id: m.id,
  senderName: m.sender?.name ?? "Unknown",
  mine: m.mine,
  body: m.body,
  fileName: m.file?.name ?? null,
  fileMime: m.file?.mime ?? null,
  deleted: m.deleted,
});

/**
 * A private chat: bubbles with day separators, newest at the bottom, the
 * composer under them. Tapping a message shows Reply, and for your own
 * recent ones Edit and Delete; a quoted reply jumps to the original.
 */
export function ChatView({ conversationId, initial }: { conversationId: string; initial: MessageOut[] }) {
  const [messages, setMessages] = useState(initial);
  const [selected, setSelected] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [replyTo, setReplyTo] = useState<MessageOut | null>(null);
  const [editing, setEditing] = useState<MessageOut | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const scroller = useRef<HTMLDivElement>(null);

  const reload = async () => {
    try {
      const r = await api<{ messages: MessageOut[] }>(`/api/conversations/${conversationId}`);
      setMessages(r.messages);
    } catch {
      // Next time.
    }
  };

  useEffect(() => {
    const timer = setInterval(reload, 15_000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  useEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages.length]);

  useEffect(() => {
    const close = (e: KeyboardEvent) => e.key === "Escape" && setSelected(null);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, []);

  const byId = new Map(messages.map((m) => [m.id, m]));

  const select = (m: MessageOut) => {
    setConfirming(false);
    setActionError(null);
    setSelected(selected === m.id ? null : m.id);
  };

  /** Scroll to a quoted message and light it up for a second. */
  const jump = (id: string) => {
    const el = document.getElementById(`m-${id}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setFlash(id);
    setTimeout(() => setFlash((f) => (f === id ? null : f)), 1000);
  };

  const remove = async (m: MessageOut) => {
    try {
      await api(`/api/messages/${m.id}`, { method: "DELETE" });
      setSelected(null);
      if (editing?.id === m.id) setEditing(null);
      if (replyTo?.id === m.id) setReplyTo(null);
      await reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not delete.");
    }
  };

  const actions = (m: MessageOut) => (
    <div className={`mt-1 flex ${m.mine ? "justify-end" : "justify-start"}`} onClick={(e) => e.stopPropagation()}>
      <div className="flex items-center gap-1 rounded-full border border-night-line bg-night-panel p-1 text-xs shadow-card">
        {confirming ? (
          <>
            <span className="px-2 text-snow-soft">Delete for both of you?</span>
            <button type="button" className="rounded-full px-3 py-1.5 text-danger hover:bg-danger/10" onClick={() => remove(m)}>
              Delete
            </button>
            <button type="button" className="rounded-full px-3 py-1.5 text-snow-faint hover:bg-night" onClick={() => setConfirming(false)}>
              Cancel
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-snow hover:bg-night"
              onClick={() => {
                setEditing(null);
                setReplyTo(m);
                setSelected(null);
              }}
            >
              <Icon name="reply" className="h-4 w-4 text-gold" /> Reply
            </button>
            {changeable(m) && (
              <>
                <button
                  type="button"
                  className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-snow hover:bg-night"
                  onClick={() => {
                    setReplyTo(null);
                    setEditing(m);
                    setSelected(null);
                  }}
                >
                  <Icon name="edit" className="h-4 w-4 text-gold" /> Edit
                </button>
                <button type="button" className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-danger hover:bg-danger/10" onClick={() => setConfirming(true)}>
                  <Icon name="trash" className="h-4 w-4" /> Delete
                </button>
              </>
            )}
          </>
        )}
      </div>
      {actionError && <p className="error ml-2 self-center">{actionError}</p>}
    </div>
  );

  const rows: React.ReactNode[] = [];
  let lastDay = "";
  for (const m of messages) {
    const day = dayOf(m.createdAt);
    if (day !== lastDay) {
      lastDay = day;
      rows.push(
        <div key={`day-${day}`} className="my-2 flex justify-center">
          <span className="chip text-snow-faint">{day}</span>
        </div>,
      );
    }
    // A quote reads as the original does now, when it is loaded here.
    const original = m.replyTo ? byId.get(m.replyTo.id) : undefined;
    rows.push(
      <Bubble key={m.id} m={m} quote={original ? asRef(original) : m.replyTo} onSelect={() => select(m)} onQuote={jump} flash={flash === m.id}>
        {selected === m.id && actions(m)}
      </Bubble>,
    );
  }

  const banner: Banner | null = editing
    ? { title: "Edit message", text: snippet(asRef(editing)), onCancel: () => setEditing(null) }
    : replyTo
      ? { title: `Replying to ${replyTo.mine ? "yourself" : replyTo.sender?.name ?? "message"}`, text: snippet(asRef(replyTo)), onCancel: () => setReplyTo(null) }
      : null;

  return (
    <>
      <div
        ref={scroller}
        className="min-h-0 flex-1 space-y-1 overflow-y-auto px-3 py-3 sm:px-4"
        onClick={(e) => e.target === e.currentTarget && setSelected(null)}
      >
        {messages.length === 0 && <p className="py-8 text-center text-sm text-snow-faint">No messages yet. Say hello.</p>}
        {rows}
      </div>
      <div className="shrink-0 border-t border-night-line p-2 sm:p-3">
        <MessageComposer
          placeholder="Message"
          banner={banner}
          editText={editing ? editing.body : null}
          send={async (draft) => {
            if (editing) {
              await api(`/api/messages/${editing.id}`, { method: "PATCH", json: { body: draft.body } });
              setEditing(null);
            } else {
              await post(`/api/conversations/${conversationId}`, { ...draft, ...(replyTo ? { replyToId: replyTo.id } : {}) });
              setReplyTo(null);
            }
            await reload();
          }}
        />
      </div>
    </>
  );
}
