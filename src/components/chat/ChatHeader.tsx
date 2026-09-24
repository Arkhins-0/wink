"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Avatar } from "../Avatar";
import { Icon } from "../Icon";
import { ChatIcon, type ChatIconName } from "./ChatIcon";

/**
 * The top of a chat: back (on a phone), the other person's or the group's
 * photo and name (which open their page), and a ⋮ menu.
 */
export function ChatHeader({
  title,
  subtitle,
  photoUrl,
  href,
  canExport,
  onSearch,
  onExport,
}: {
  title: string;
  subtitle: string;
  photoUrl: string | null;
  href: string;
  canExport: boolean;
  onSearch: () => void;
  onExport: () => void;
}) {
  const [open, setOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);

  // Any click outside the menu closes it.
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !menu.current?.contains(e.target as Node) && setOpen(false);
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    window.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", close);
      window.removeEventListener("keydown", esc);
    };
  }, [open]);

  const item = (label: string, act: () => void) => (
    <button
      type="button"
      className="row w-full text-left text-sm"
      onClick={() => {
        setOpen(false);
        act();
      }}
    >
      {label}
    </button>
  );

  return (
    <div className="flex h-[60px] shrink-0 items-center gap-2 border-b border-night-line px-3">
      <Link href="/chats" className="btn-icon lg:hidden" aria-label="Back to chats">
        <Icon name="back" className="h-5 w-5" />
      </Link>
      <Link href={href} className="flex min-w-0 flex-1 items-center gap-3 rounded-xl py-1 pr-2 hover:bg-snow/5">
        <Avatar src={photoUrl} name={title} size={38} />
        <span className="min-w-0">
          <span className="block truncate text-sm font-semibold">{title}</span>
          <span className="block truncate text-xs text-snow-faint">{subtitle}</span>
        </span>
      </Link>
      <div ref={menu} className="relative">
        <button type="button" className="btn-icon" title="More" aria-label="More" aria-expanded={open} onClick={() => setOpen(!open)}>
          <ChatIcon name="more" className="h-5 w-5" />
        </button>
        {open && (
          <div className="absolute right-0 top-full z-20 mt-1 w-48 rounded-xl border border-night-line bg-night-panel p-1 shadow-card">
            {item("Search messages", onSearch)}
            {canExport && item("Export chat", onExport)}
          </div>
        )}
      </div>
    </div>
  );
}

export type SelectionAction = { label: string; icon: ChatIconName | "reply" | "edit" | "trash"; onClick: () => void; enabled?: boolean };

/** While messages are selected the header becomes this: ✕ and the count, then what can be done with them. */
export function SelectionBar({ count, onClose, actions }: { count: number; onClose: () => void; actions: SelectionAction[] }) {
  return (
    <div className="flex h-[60px] shrink-0 items-center gap-1 border-b border-night-line bg-night-panel px-2">
      <button type="button" className="btn-icon" title="Clear selection" aria-label="Clear selection" onClick={onClose}>
        <Icon name="close" className="h-5 w-5" />
      </button>
      <span className="ml-1 min-w-0 flex-1 truncate text-sm font-semibold">{count}</span>
      {actions.map((a) => {
        const enabled = a.enabled !== false;
        const glyph =
          a.icon === "reply" || a.icon === "edit" || a.icon === "trash" ? <Icon name={a.icon} className="h-5 w-5" /> : <ChatIcon name={a.icon} className="h-5 w-5" />;
        return (
          <button
            key={a.label}
            type="button"
            className={`btn-icon ${enabled ? "text-snow" : "cursor-not-allowed opacity-35"}`}
            title={enabled ? a.label : `${a.label} (only within 2 hours of sending)`}
            aria-label={a.label}
            disabled={!enabled}
            onClick={a.onClick}
          >
            {glyph}
          </button>
        );
      })}
    </div>
  );
}
