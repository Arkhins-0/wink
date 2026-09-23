"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageComposer, post } from "./MessageComposer";
import { MessageItem } from "./MessageList";

/** The weekend channel: posts from admins and coordinators, read by everyone. */
export function ChannelView({ weekendId, initial, canPost, open }: { weekendId: string; initial: MessageOut[]; canPost: boolean; open: boolean }) {
  const [messages, setMessages] = useState(initial);

  const reload = async () => {
    try {
      const r = await api<{ messages: MessageOut[] }>(`/api/weekends/${weekendId}/channel`);
      setMessages(r.messages);
    } catch {
      // Next time.
    }
  };

  useEffect(() => {
    const timer = setInterval(reload, 20_000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [weekendId]);

  return (
    <div className="space-y-3">
      {canPost && (
        <MessageComposer
          placeholder="Post to everyone for this weekend"
          submitLabel="Post"
          send={async (draft) => {
            await post(`/api/weekends/${weekendId}/channel`, draft);
            await reload();
          }}
        />
      )}
      {!open && <p className="text-xs text-snow-faint">This channel is closed.</p>}
      {messages.length === 0 && <p className="card text-sm text-snow-faint">No posts yet.</p>}
      {[...messages].reverse().map((m) => (
        <MessageItem key={m.id} m={m} />
      ))}
    </div>
  );
}
