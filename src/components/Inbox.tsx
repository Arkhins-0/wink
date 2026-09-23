"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageItem } from "./MessageList";

/**
 * The inbox: newest first, what has come into view gets marked read, and
 * a short poll keeps it current while the page is open.
 */
export function Inbox({ initial, highlight }: { initial: MessageOut[]; highlight?: string }) {
  const [messages, setMessages] = useState(initial);
  const [more, setMore] = useState(initial.length >= 60);
  const [busy, setBusy] = useState(false);
  const marked = useRef<Set<string>>(new Set());

  // Mark unread ones read once they are on screen.
  useEffect(() => {
    const unread = messages.filter((m) => !m.readAt && !m.mine && !marked.current.has(m.id)).map((m) => m.id);
    if (unread.length === 0) return;
    unread.forEach((id) => marked.current.add(id));
    api("/api/messages/read", { method: "POST", json: { ids: unread } }).catch(() => null);
  }, [messages]);

  useEffect(() => {
    if (!highlight) return;
    document.getElementById(`m-${highlight}`)?.scrollIntoView({ block: "center" });
  }, [highlight]);

  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const r = await api<{ messages: MessageOut[] }>("/api/messages");
        setMessages((current) => {
          const known = new Set(current.map((m) => m.id));
          const fresh = r.messages.filter((m) => !known.has(m.id));
          return fresh.length ? [...fresh, ...current] : current;
        });
      } catch {
        // Next tick.
      }
    }, 20_000);
    return () => clearInterval(timer);
  }, []);

  const loadMore = async () => {
    const last = messages[messages.length - 1];
    if (!last || busy) return;
    setBusy(true);
    try {
      const r = await api<{ messages: MessageOut[] }>(`/api/messages?before=${encodeURIComponent(last.createdAt)}`);
      setMessages((c) => [...c, ...r.messages]);
      setMore(r.messages.length >= 60);
    } finally {
      setBusy(false);
    }
  };

  if (messages.length === 0) return <p className="card text-sm text-snow-faint">Nothing yet. Messages sent to you appear here.</p>;

  return (
    <div className="space-y-3">
      {messages.map((m) => (
        <MessageItem key={m.id} m={m} highlight={m.id === highlight} />
      ))}
      {more && (
        <button className="btn-ghost w-full" onClick={loadMore} disabled={busy}>
          {busy ? "Loading…" : "Older messages"}
        </button>
      )}
    </div>
  );
}
