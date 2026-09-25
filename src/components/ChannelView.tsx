"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { MessageComposer, post } from "./MessageComposer";
import { MessageItem } from "./MessageList";

/** Why a channel is closed: its season is archived now, it was closed with its season, or an admin closed it. */
export type ClosedReason = "archived" | "season" | "admin" | null;

type Channel = { open: boolean; closedReason: ClosedReason; canPost: boolean };

const CLOSED_LINE: Record<Exclude<ClosedReason, null>, string> = {
  archived: "This channel is closed: its season is archived.",
  season: "This channel was closed when its season was archived.",
  admin: "This channel was closed by an admin.",
};

/** The weekend channel: posts from admins, coordinators and the weekend's channel managers, read by everyone. */
export function ChannelView({
  weekendId,
  initial,
  canPost,
  open,
  closedReason,
  isAdmin,
}: {
  weekendId: string;
  initial: MessageOut[];
  canPost: boolean;
  open: boolean;
  closedReason: ClosedReason;
  isAdmin: boolean;
}) {
  const router = useRouter();
  const [messages, setMessages] = useState(initial);
  const [channel, setChannel] = useState<Channel>({ open, closedReason, canPost });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The page re-renders after the weekend menu opens or closes the channel: take its word.
  useEffect(() => setChannel({ open, closedReason, canPost }), [open, closedReason, canPost]);

  const reload = async () => {
    try {
      const r = await api<{ messages: MessageOut[] } & Channel>(`/api/weekends/${weekendId}/channel`);
      setMessages(r.messages);
      setChannel({ open: r.open, closedReason: r.closedReason, canPost: r.canPost });
    } catch {
      // Next time.
    }
  };

  const openChannel = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/weekends/${weekendId}/channel`, { method: "PATCH", json: { open: true } });
      await reload();
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open the channel.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    const timer = setInterval(reload, 20_000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [weekendId]);

  return (
    <div className="space-y-3">
      {channel.canPost && (
        <MessageComposer
          placeholder="Post to everyone for this weekend"
          submitLabel="Post"
          send={async (draft) => {
            await post(`/api/weekends/${weekendId}/channel`, draft);
            await reload();
          }}
        />
      )}
      {!channel.open && (
        <div className="flex items-center gap-3">
          <p className="min-w-0 flex-1 text-xs text-snow-faint">{CLOSED_LINE[channel.closedReason ?? "admin"]}</p>
          {/* A channel in an archived season opens only once the season is brought back. */}
          {isAdmin && channel.closedReason !== "archived" && (
            <button className="btn-ghost shrink-0 px-3 py-1 text-xs" onClick={openChannel} disabled={busy}>
              {busy ? "Opening…" : "Open channel"}
            </button>
          )}
        </div>
      )}
      {error && <p className="error">{error}</p>}
      {messages.length === 0 && <p className="card text-sm text-snow-faint">No posts yet.</p>}
      {[...messages].reverse().map((m) => (
        <MessageItem key={m.id} m={m} inPlace />
      ))}
    </div>
  );
}
