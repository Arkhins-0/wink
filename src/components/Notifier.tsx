"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { WebFirebaseConfig } from "@/lib/firebaseWeb";
import type { MessageOut } from "@/lib/messages";

const POLL_MS = 20_000;

function linkFor(m: MessageOut): string {
  if (m.kind === "direct" && m.conversationId) return `/chats/${m.conversationId}`;
  if (m.kind === "channel" && m.weekendId) return `/w/${m.weekendId}`;
  return `/home?m=${m.id}`;
}

/**
 * The popup. New messages arrive by push (Firebase, when the deployment
 * has it) and by a short poll, so nothing is missed while a tab is open.
 * Whatever is new is shown in one panel with a way to open it.
 */
export function Notifier({ onUnread }: { onUnread?: (home: number, chats: number) => void }) {
  const [fresh, setFresh] = useState<MessageOut[]>([]);
  const since = useRef<string | null>(null);
  const seen = useRef<Set<string>>(new Set());

  useEffect(() => {
    let alive = true;

    const poll = async () => {
      try {
        const r = await api<{ messages: MessageOut[]; unread: number; unreadHome: number; unreadChats: number; now: string }>(
          `/api/messages/unseen${since.current ? `?since=${encodeURIComponent(since.current)}` : ""}`,
        );
        if (!alive) return;
        onUnread?.(r.unreadHome, r.unreadChats);
        const first = since.current === null;
        since.current = r.now;
        // The first poll only sets the clock: old unread mail is in the inbox, not a popup.
        if (first) return;
        const unseen = r.messages.filter((m) => !seen.current.has(m.id));
        unseen.forEach((m) => seen.current.add(m.id));
        if (unseen.length > 0) setFresh((f) => [...unseen, ...f].slice(0, 5));
      } catch {
        // Offline or signed out: the next poll will say.
      }
    };
    poll();
    const timer = setInterval(poll, POLL_MS);

    // Push, when it is set up: register this browser and show foreground messages at once.
    let unsubscribe: (() => void) | undefined;
    (async () => {
      try {
        if (!("serviceWorker" in navigator)) return;
        // Registered on every visit: it is what makes the site installable; push rides on it when set up.
        const registration = await navigator.serviceWorker.register("/firebase-messaging-sw.js");
        if (typeof Notification === "undefined" || Notification.permission !== "granted") return;
        const { firebase } = await api<{ firebase: WebFirebaseConfig | null }>("/api/config");
        if (!firebase || !alive) return;
        const { initializeApp, getApps } = await import("firebase/app");
        const { getMessaging, getToken, onMessage, isSupported } = await import("firebase/messaging");
        if (!(await isSupported())) return;
        const app = getApps()[0] ?? initializeApp({
          apiKey: firebase.apiKey,
          projectId: firebase.projectId,
          messagingSenderId: firebase.messagingSenderId,
          appId: firebase.appId,
        });
        const messaging = getMessaging(app);
        const token = await getToken(messaging, { vapidKey: firebase.vapidKey, serviceWorkerRegistration: registration });
        if (token) await api("/api/push/register", { method: "POST", json: { token, platform: "web" } });
        unsubscribe = onMessage(messaging, () => {
          poll();
          // An open chat reloads at once instead of waiting for its next look.
          window.dispatchEvent(new Event("wink:push"));
        });
      } catch (error) {
        console.warn("[push] not available", error);
      }
    })();

    return () => {
      alive = false;
      clearInterval(timer);
      unsubscribe?.();
    };
  }, [onUnread]);

  if (fresh.length === 0) return null;
  const top = fresh[0];
  const more = fresh.length - 1;

  return (
    <div className="fixed inset-x-0 top-0 z-40 flex justify-center px-3 pt-3" role="status">
      <div className="card w-full max-w-md border-gold/40 p-4 shadow-glow">
        <div className="flex items-start gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-gold">
              {top.urgent ? "Urgent · " : ""}
              {top.sender?.name ?? "Wink"}
              {top.sender?.roleLabel ? ` · ${top.sender.roleLabel}` : ""}
            </p>
            <p className="mt-1 line-clamp-3 text-sm">{top.body || (top.file ? `Document: ${top.file.name}` : "New message")}</p>
            {more > 0 && <p className="mt-1 text-xs text-snow-faint">and {more} more</p>}
          </div>
          <button className="text-snow-faint hover:text-snow" onClick={() => setFresh([])} aria-label="Dismiss">
            ✕
          </button>
        </div>
        <div className="mt-3 flex gap-2">
          <Link href={linkFor(top)} className="btn-gold px-4 py-2 text-xs" onClick={() => setFresh([])}>
            Open
          </Link>
          {more > 0 && (
            <Link href="/home" className="btn-ghost px-4 py-2 text-xs" onClick={() => setFresh([])}>
              Inbox
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
