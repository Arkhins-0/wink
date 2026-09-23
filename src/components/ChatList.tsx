"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { timeAgo } from "@/lib/client";
import type { ConversationOut } from "@/lib/messages";
import { Avatar } from "./Avatar";
import { Icon } from "./Icon";

/** The chat list pane, with the open conversation (or the new-chat picker) beside it. */
export function ChatList({ conversations, canOpen, children }: { conversations: ConversationOut[]; canOpen: boolean; children: React.ReactNode }) {
  const pathname = usePathname();
  const [filter, setFilter] = useState("");
  const onList = pathname === "/chats";
  const shown = conversations.filter((c) => !filter || `${c.other.name} ${c.other.roleLabel} ${c.lastMessage ?? ""}`.toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="grid h-full min-h-0 gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
      <aside className={`${onList ? "flex" : "hidden lg:flex"} card h-full min-h-0 flex-col p-0`}>
        <div className="flex items-center gap-2 border-b border-night-line p-3">
          <div className="relative flex-1">
            <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
            <input className="input pl-9" placeholder="Search chats" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          {canOpen && (
            <Link href="/chats/new" className="btn-icon bg-gold text-night hover:bg-gold-deep hover:text-night" title="New chat" aria-label="New chat">
              <Icon name="edit" className="h-4 w-4" />
            </Link>
          )}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {shown.length === 0 && (
            <p className="p-3 text-sm text-snow-faint">{canOpen ? "No chats yet. Start one with the pencil." : "Race officials do not have private chats."}</p>
          )}
          {shown.map((c) => {
            const active = pathname === `/chats/${c.id}`;
            return (
              <Link key={c.id} href={`/chats/${c.id}`} className={`row ${active ? "bg-snow/10" : ""}`}>
                <Avatar src={c.other.photoUrl} name={c.other.name} size={44} />
                <span className="min-w-0 flex-1">
                  <span className="flex items-baseline justify-between gap-2">
                    <span className="truncate text-sm font-medium">{c.other.name}</span>
                    {c.lastMessageAt && <span className={`shrink-0 text-[11px] ${c.unread > 0 ? "text-gold" : "text-snow-faint"}`}>{timeAgo(c.lastMessageAt)}</span>}
                  </span>
                  <span className="flex items-center justify-between gap-2">
                    <span className={`truncate text-xs ${c.unread > 0 ? "text-snow" : "text-snow-faint"}`}>{c.lastMessage ?? c.other.roleLabel}</span>
                    {c.unread > 0 && <span className="badge">{c.unread}</span>}
                  </span>
                </span>
              </Link>
            );
          })}
        </div>
      </aside>
      <section className={`${onList ? "hidden lg:flex" : "flex"} h-full min-h-0 flex-col`}>{children}</section>
    </div>
  );
}
