"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageComposer, post } from "./MessageComposer";
import { MessageItem } from "./MessageList";

/** A private chat, oldest at the top, the composer at the bottom. */
export function ChatView({ conversationId, initial }: { conversationId: string; initial: MessageOut[] }) {
  const [messages, setMessages] = useState(initial);
  const end = useRef<HTMLDivElement>(null);

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
    end.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  return (
    <div className="space-y-3">
      {messages.length === 0 && <p className="card text-sm text-snow-faint">No messages yet.</p>}
      {messages.map((m) => (
        <MessageItem key={m.id} m={m} />
      ))}
      <div ref={end} />
      <MessageComposer
        send={async (draft) => {
          await post(`/api/conversations/${conversationId}`, draft);
          await reload();
        }}
      />
    </div>
  );
}
