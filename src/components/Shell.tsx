"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useState } from "react";
import { APP_NAME } from "@/lib/config";
import { CountdownChip } from "./CountdownChip";
import { Notifier } from "./Notifier";
import { PermissionGate } from "./PermissionGate";

const NAV = [
  { href: "/home", label: "Home" },
  { href: "/schedule", label: "Schedule" },
  { href: "/chats", label: "Chats" },
  { href: "/people", label: "People" },
  { href: "/account", label: "Account" },
];

/** The signed-in frame: a top bar with the countdown, a nav, the popup. */
export function Shell({ unread: initialUnread, children }: { unread: number; children: React.ReactNode }) {
  const pathname = usePathname();
  const [unread, setUnread] = useState(initialUnread);
  const onUnread = useCallback((n: number) => setUnread(n), []);

  return (
    <PermissionGate>
      <Notifier onUnread={onUnread} />
      <div className="flex min-h-screen flex-col">
        <header className="sticky top-0 z-30 border-b border-night-line bg-night/90 backdrop-blur">
          <div className="container-x flex h-14 items-center justify-between gap-3">
            <Link href="/home" className="flex items-center gap-2.5">
              <Image src="/ctr-logo.png" alt="" width={36} height={20} priority />
              <span className="font-semibold tracking-tight">{APP_NAME}</span>
            </Link>
            <CountdownChip />
          </div>
        </header>

        <main className="container-x flex-1 py-5 pb-24 sm:pb-8">{children}</main>

        <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-night-line bg-night/95 backdrop-blur sm:static sm:border-t-0 sm:bg-transparent">
          <div className="container-x flex justify-around py-2 sm:justify-center sm:gap-2 sm:py-4">
            {NAV.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`relative rounded-full px-3 py-1.5 text-xs font-semibold sm:text-sm ${
                    active ? "bg-snow/10 text-snow" : "text-snow-faint hover:text-snow"
                  }`}
                >
                  {item.label}
                  {item.href === "/home" && unread > 0 && (
                    <span className="absolute -right-1 -top-1 rounded-full bg-gold px-1.5 text-[10px] font-bold text-night">
                      {unread > 99 ? "99+" : unread}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </nav>
      </div>
    </PermissionGate>
  );
}
