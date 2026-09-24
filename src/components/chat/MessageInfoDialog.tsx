"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { Avatar } from "../Avatar";
import { Modal } from "./Modal";

type Recipient = { id: string; name: string; roleLabel: string; photoUrl: string | null; deliveredAt: string | null; readAt: string | null };
type Info = { sentAt: string; recipients: Recipient[] };

const when = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit", hour12: true });

/**
 * Message info, as WhatsApp has it: when a message you sent went, who it
 * has not reached yet, whose phone has it and who has read it. It stays
 * live while open: a look every few seconds, and at once on a push.
 */
export function MessageInfoDialog({ messageId, preview, onClose }: { messageId: string; preview: string; onClose: () => void }) {
  const [info, setInfo] = useState<Info | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    const load = () =>
      api<Info>(`/api/messages/${messageId}/info`)
        .then((i) => {
          if (!live) return;
          setInfo(i);
          setError(null);
        })
        .catch((e) => live && setError(e instanceof Error ? e.message : "Could not load the message info."));
    load();
    const timer = setInterval(load, 4_000);
    window.addEventListener("wink:push", load);
    return () => {
      live = false;
      clearInterval(timer);
      window.removeEventListener("wink:push", load);
    };
  }, [messageId]);

  const section = (title: string, people: Recipient[], time: (r: Recipient) => string | null) =>
    people.length > 0 && (
      <div className="mt-4">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-gold">{title}</p>
        {people.map((p) => {
          const at = time(p);
          return (
            <div key={p.id} className="mt-2 flex items-center gap-3">
              <Avatar src={p.photoUrl} name={p.name} size={36} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{p.name}</span>
                <span className="block truncate text-xs text-snow-faint">{p.roleLabel}</span>
              </span>
              {at && <span className="shrink-0 text-xs text-snow-soft">{when(at)}</span>}
            </div>
          );
        })}
      </div>
    );

  return (
    <Modal onClose={onClose} className="flex max-h-[85vh] max-w-md flex-col">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="font-semibold">Message info</p>
          <p className="mt-0.5 line-clamp-2 text-xs text-snow-soft">{preview}</p>
        </div>
        <button type="button" className="btn-ghost px-3 py-1.5 text-xs" onClick={onClose}>
          Close
        </button>
      </div>
      <div className="mt-2 min-h-0 flex-1 overflow-y-auto">
        {error && !info && <p className="error mt-2">{error}</p>}
        {!info && !error && <p className="mt-3 text-sm text-snow-faint">Loading…</p>}
        {info && (
          <>
            {/* The way the message travels: sent, then waiting, then on their phone, then read. */}
            <div className="mt-3">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gold">Sent</p>
              <p className="mt-1 text-sm">{when(info.sentAt)}</p>
            </div>
            {section("Not delivered", info.recipients.filter((r) => !r.deliveredAt), () => null)}
            {section("Delivered to", info.recipients.filter((r) => !r.readAt && r.deliveredAt), (r) => r.deliveredAt)}
            {section("Read by", info.recipients.filter((r) => r.readAt), (r) => r.readAt)}
          </>
        )}
      </div>
    </Modal>
  );
}
