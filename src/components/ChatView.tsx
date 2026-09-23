"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageComposer, post } from "./MessageComposer";
import { Bubble } from "./MessageList";

const dayOf = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });

/** A private chat: bubbles with day separators, newest at the bottom, the composer under them. */
export function ChatView({ conversationId, initial }: { conversationId: string; initial: MessageOut[] }) {
  const [messages, setMessages] = useState(initial);
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
    rows.push(<Bubble key={m.id} m={m} />);
  }

  return (
    <>
      <div ref={scroller} className="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-3 py-3 sm:px-4">
        {messages.length === 0 && <p className="py-8 text-center text-sm text-snow-faint">No messages yet. Say hello.</p>}
        {rows}
      </div>
      <div className="border-t border-night-line p-2 sm:p-3">
        <MessageComposer
          placeholder="Message"
          send={async (draft) => {
            await post(`/api/conversations/${conversationId}`, draft);
            await reload();
          }}
        />
      </div>
    </>
  );
}
