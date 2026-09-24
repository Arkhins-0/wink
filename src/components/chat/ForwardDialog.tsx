"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { ConversationOut } from "@/lib/messages";
import { Avatar } from "../Avatar";
import { Icon } from "../Icon";
import { Modal } from "./Modal";

/** Pick the chats to forward to: the people talked to most first, then whoever spoke last. */
export function ForwardDialog({ count, onClose, onSend }: { count: number; onClose: () => void; onSend: (targets: ConversationOut[]) => void }) {
  const [chats, setChats] = useState<ConversationOut[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");

  useEffect(() => {
    api<{ conversations: ConversationOut[] }>("/api/conversations")
      .then((r) => setChats(r.conversations))
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load your chats."));
  }, []);

  const needle = search.trim().toLowerCase();
  const list = chats
    ?.slice()
    .sort((a, b) => b.messages - a.messages || (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? ""))
    .filter((c) => !needle || c.other.name.toLowerCase().includes(needle) || c.other.roleLabel.toLowerCase().includes(needle));

  const flip = (id: string) =>
    setPicked((p) => {
      const next = new Set(p);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  return (
    <Modal onClose={onClose} className="flex max-h-[85vh] max-w-md flex-col">
      <p className="font-semibold">Forward {count === 1 ? "message" : `${count} messages`} to…</p>
      <div className="relative mt-3">
        <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
        <input className="input pl-9" placeholder="Search people" value={search} onChange={(e) => setSearch(e.target.value)} autoFocus />
      </div>
      <div className="-mx-2 mt-2 min-h-0 flex-1 overflow-y-auto">
        {error && <p className="error mx-2">{error}</p>}
        {!list && !error && <p className="p-3 text-sm text-snow-faint">Loading…</p>}
        {list?.length === 0 && <p className="p-3 text-sm text-snow-faint">{needle ? "No one matches." : "No chats to forward to."}</p>}
        {list?.map((c) => (
          <label key={c.id} className="row cursor-pointer">
            <Avatar src={c.other.photoUrl} name={c.other.name} size={40} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{c.other.name}</span>
              <span className="block truncate text-xs text-snow-faint">{c.other.roleLabel}</span>
            </span>
            <input type="checkbox" className="h-4 w-4 accent-gold" checked={picked.has(c.id)} onChange={() => flip(c.id)} />
          </label>
        ))}
      </div>
      <div className="mt-4 flex gap-2">
        <button type="button" className="btn-ghost" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="btn-gold flex-1" disabled={picked.size === 0} onClick={() => onSend((chats ?? []).filter((c) => picked.has(c.id)))}>
          {picked.size > 1 ? `Forward to ${picked.size} chats` : "Forward"}
        </button>
      </div>
    </Modal>
  );
}
