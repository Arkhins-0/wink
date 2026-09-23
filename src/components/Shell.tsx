"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useState } from "react";
import { APP_NAME } from "@/lib/config";
import { Avatar } from "./Avatar";
import { CountdownChip } from "./CountdownChip";
import { Icon, type IconName } from "./Icon";
import { Notifier } from "./Notifier";
import { PermissionGate } from "./PermissionGate";

const NAV: { href: string; label: string; icon: IconName }[] = [
  { href: "/home", label: "Home", icon: "home" },
  { href: "/schedule", label: "Schedule", icon: "calendar" },
  { href: "/chats", label: "Chats", icon: "chat" },
  { href: "/people", label: "People", icon: "people" },
  { href: "/archive", label: "Archive", icon: "archive" },
];

export type ShellUser = { name: string; roleLabel: string; photoUrl: string | null };

/**
 * The signed-in frame. On a laptop: a sidebar with the navigation and the
 * person at the bottom, a slim top bar with the countdown, and the page.
 * On a phone: a top bar and a bottom bar of icons, the account tab being
 * the person's photo.
 */
export function Shell({ user, unreadHome, unreadChats, children }: { user: ShellUser; unreadHome: number; unreadChats: number; children: React.ReactNode }) {
  const pathname = usePathname();
  const [badges, setBadges] = useState({ home: unreadHome, chats: unreadChats });
  const onUnread = useCallback((home: number, chats: number) => setBadges({ home, chats }), []);
  const active = (href: string) => pathname === href || pathname.startsWith(`${href}/`);
  const badge = (href: string) => (href === "/home" ? badges.home : href === "/chats" ? badges.chats : 0);
  // A chat thread wants the whole phone screen: no bottom bar under the composer.
  const immersive = /^\/chats\/[^/]+$/.test(pathname);

  return (
    <PermissionGate>
      <Notifier onUnread={onUnread} />
      <div className="flex min-h-screen">
        <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-night-line bg-night-panel/40 lg:flex">
          <Link href="/home" className="flex items-center gap-3 px-6 py-6">
            <Image src="/ctr-logo.png" alt="" width={44} height={25} priority />
            <span className="text-lg font-semibold tracking-tight">{APP_NAME}</span>
          </Link>
          <nav className="flex-1 space-y-1 px-3">
            {NAV.map((item) => (
              <Link key={item.href} href={item.href} className={`nav-link ${active(item.href) ? "nav-link-active" : ""}`}>
                <Icon name={item.icon} className={`h-5 w-5 ${active(item.href) ? "text-gold" : ""}`} />
                <span className="flex-1">{item.label}</span>
                {badge(item.href) > 0 && <span className="badge">{badge(item.href) > 99 ? "99+" : badge(item.href)}</span>}
              </Link>
            ))}
          </nav>
          <Link href="/account" className={`m-3 flex items-center gap-3 rounded-xl px-3 py-3 transition-colors hover:bg-snow/5 ${active("/account") ? "bg-snow/10" : ""}`}>
            <Avatar src={user.photoUrl} name={user.name} size={36} />
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{user.name}</span>
              <span className="block truncate text-xs text-snow-faint">{user.roleLabel}</span>
            </span>
          </Link>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-30 border-b border-night-line bg-night/90 backdrop-blur">
            <div className="flex h-14 items-center justify-between gap-3 px-4 sm:px-6">
              <Link href="/home" className="flex items-center gap-2.5 lg:hidden">
                <Image src="/ctr-logo.png" alt="" width={36} height={20} priority />
                <span className="font-semibold tracking-tight">{APP_NAME}</span>
              </Link>
              <span className="hidden text-sm text-snow-faint lg:block">{NAV.find((n) => active(n.href))?.label ?? (active("/account") ? "Account" : "")}</span>
              <CountdownChip />
            </div>
          </header>

          <main className={`flex-1 px-4 py-5 sm:px-6 lg:px-8 lg:py-6 ${immersive ? "pb-2 pt-3 lg:pb-8 lg:pt-6" : "pb-24 lg:pb-8"}`}>
            <div className="mx-auto w-full max-w-6xl">{children}</div>
          </main>
        </div>
      </div>

      {!immersive && (
        <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-night-line bg-night/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden">
          <div className="flex items-center justify-around py-2">
            {NAV.filter((n) => n.href !== "/archive").map((item) => (
              <Link key={item.href} href={item.href} aria-label={item.label} className={`relative rounded-2xl p-3 ${active(item.href) ? "bg-snow/10 text-gold" : "text-snow-faint"}`}>
                <Icon name={item.icon} className="h-6 w-6" />
                {badge(item.href) > 0 && <span className="badge absolute -right-1 -top-1">{badge(item.href) > 99 ? "99+" : badge(item.href)}</span>}
              </Link>
            ))}
            <Link href="/account" aria-label="Account" className={`rounded-2xl p-2 ${active("/account") ? "bg-snow/10" : ""}`}>
              <span className={`block rounded-full border-2 ${active("/account") ? "border-gold" : "border-night-line"}`}>
                <Avatar src={user.photoUrl} name={user.name} size={30} />
              </span>
            </Link>
          </div>
        </nav>
      )}
    </PermissionGate>
  );
}
