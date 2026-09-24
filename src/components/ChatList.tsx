"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import type { ConversationOut } from "@/lib/messages";
import { Avatar } from "./Avatar";
import { Icon } from "./Icon";

type Filter = "all" | "unread" | "groups";

const FILTERS: { key: Filter; label: string }[] = [
  { key: "all", label: "All" },
  { key: "unread", label: "Unread" },
  { key: "groups", label: "Groups" },
];

/**
 * The chat list pane, with the open conversation (or the new-chat picker)
 * beside it. The channels page takes the whole width instead: it is the
 * other half of the same section, one tab over.
 */
export function ChatList({ conversations, canOpen, children }: { conversations: ConversationOut[]; canOpen: boolean; children: React.ReactNode }) {
  const pathname = usePathname();
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const onList = pathname === "/chats";

  if (pathname === "/chats/channels") {
    return (
      <div className="flex h-full min-h-0 flex-col">
        <ChatsTabs />
        <section className="flex min-h-0 flex-1 flex-col">{children}</section>
      </div>
    );
  }

  const shown = conversations.filter(
    (c) =>
      (filter === "all" || (filter === "unread" ? c.unread > 0 : c.kind === "group")) &&
      (!search || `${c.other.name} ${c.other.roleLabel} ${c.lastMessage ?? ""}`.toLowerCase().includes(search.toLowerCase())),
  );

  return (
    <div className="grid h-full min-h-0 gap-6 lg:grid-cols-[22rem_minmax(0,1fr)]">
      <aside className={`${onList ? "flex" : "hidden lg:flex"} -mx-4 h-full min-h-0 flex-col sm:-mx-6 lg:mx-0 lg:border-r lg:border-night-line lg:pr-2`}>
        <div className="px-4 sm:px-6 lg:px-0">
          <ChatsTabs />
        </div>
        <div className="flex items-center gap-2 px-4 pb-2 sm:px-6 lg:px-0">
          <div className="relative flex-1">
            <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
            <input className="input pl-9" placeholder="Search chats" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          {canOpen && (
            <Link href="/chats/new" className="btn-icon bg-gold text-night hover:bg-gold-deep hover:text-night" title="New chat" aria-label="New chat">
              <Icon name="edit" className="h-4 w-4" />
            </Link>
          )}
        </div>
        <div className="flex gap-2 px-4 pb-2 sm:px-6 lg:px-0" role="tablist" aria-label="Show">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              role="tab"
              aria-selected={filter === f.key}
              className={`chip transition-colors ${filter === f.key ? "border-gold bg-gold text-night" : "text-snow-soft hover:text-snow"}`}
              onClick={() => setFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {shown.length === 0 && (
            <p className="px-4 py-3 text-sm text-snow-faint sm:px-6 lg:px-3">
              {filter === "unread"
                ? "Nothing unread."
                : filter === "groups"
                  ? "No groups yet."
                  : search
                    ? "No chats match."
                    : canOpen
                      ? "No chats yet. Start one with the pencil."
                      : "Race officials do not have private chats."}
            </p>
          )}
          {shown.map((c, i) => (
            <ChatRow key={c.id} c={c} active={pathname === `/chats/${c.id}` || pathname.startsWith(`/chats/${c.id}/`)} divider={i > 0} />
          ))}
        </div>
      </aside>
      <section className={`${onList ? "hidden lg:flex" : "flex"} h-full min-h-0 flex-col`}>{children}</section>
    </div>
  );
}

/** One chat: no box around it, just a line under the one above, starting after the picture. */
function ChatRow({ c, active, divider }: { c: ConversationOut; active: boolean; divider: boolean }) {
  return (
    <div>
      {divider && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
      <Link
        href={`/chats/${c.id}`}
        className={`flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3 ${active ? "bg-snow/10" : ""}`}
      >
        <Avatar src={c.other.photoUrl} name={c.other.name} size={48} />
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline justify-between gap-2">
            <span className="truncate text-[15px] font-medium">{c.other.name}</span>
            {c.lastMessageAt && (
              <span className={`shrink-0 text-[11px] ${c.unread > 0 ? "text-gold" : "text-snow-faint"}`}>
                <WhenLabel iso={c.lastMessageAt} />
              </span>
            )}
          </span>
          <span className="mt-0.5 flex items-center justify-between gap-2">
            <span className={`flex min-w-0 items-center gap-1 text-[13px] ${c.unread > 0 ? "text-snow" : "text-snow-faint"}`}>
              {c.lastStatus && <ListTicks status={c.lastStatus} />}
              <span className="truncate">{c.lastMessage ?? c.other.roleLabel}</span>
            </span>
            {c.unread > 0 && <span className="badge shrink-0">{c.unread > 99 ? "99+" : c.unread}</span>}
          </span>
        </span>
      </Link>
    </div>
  );
}

/** The "Chats | Channels" switch at the top of the section. */
export function ChatsTabs() {
  const pathname = usePathname();
  const channels = pathname.startsWith("/chats/channels");
  const tab = (on: boolean) =>
    `relative pb-2 text-lg font-semibold tracking-tight transition-colors ${on ? "text-snow after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-gold" : "text-snow-faint hover:text-snow-soft"}`;
  return (
    <nav className="mb-3 flex items-center gap-3" aria-label="Chats or channels">
      <Link href="/chats" className={tab(!channels)} aria-current={!channels ? "page" : undefined}>
        Chats
      </Link>
      <span className="pb-2 text-lg text-night-line" aria-hidden>
        |
      </span>
      <Link href="/chats/channels" className={tab(channels)} aria-current={channels ? "page" : undefined}>
        Channels
      </Link>
    </nav>
  );
}

const TICK_LABEL = { sent: "Sent", delivered: "Delivered", read: "Read" } as const;

/**
 * The same ticks the messages carry (MessageList's Ticks), drawn in the
 * list's grey instead of the gold bubble's dark: one sent, two delivered,
 * three read in blue.
 */
function ListTicks({ status }: { status: keyof typeof TICK_LABEL }) {
  const n = status === "read" ? 3 : status === "delivered" ? 2 : 1;
  return (
    <svg
      viewBox={`0 0 ${10 + (n - 1) * 5} 10`}
      className={`inline-block h-2.5 shrink-0 ${status === "read" ? "text-sky-400" : "text-snow-faint"}`}
      style={{ width: `${(10 + (n - 1) * 5) * 1.1}px` }}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      role="img"
      aria-label={TICK_LABEL[status]}
    >
      <title>{TICK_LABEL[status]}</title>
      {Array.from({ length: n }, (_, i) => (
        <path key={i} d={`M${1 + i * 5} 5.5l2.5 2.5L${9 + i * 5} 2`} />
      ))}
    </svg>
  );
}

/** Today's time (12-hour), "Yesterday", the weekday this week, else the date. After mount, in the reader's zone. */
export function WhenLabel({ iso }: { iso: string }) {
  const [text, setText] = useState("");
  useEffect(() => {
    const d = new Date(iso);
    const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
    const days = Math.round((day(new Date()) - day(d)) / 86_400_000);
    setText(
      days <= 0
        ? d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit", hour12: true })
        : days === 1
          ? "Yesterday"
          : days < 7
            ? d.toLocaleDateString(undefined, { weekday: "short" })
            : d.toLocaleDateString(undefined, { day: "numeric", month: "short" }),
    );
  }, [iso]);
  return <time dateTime={iso}>{text}</time>;
}
