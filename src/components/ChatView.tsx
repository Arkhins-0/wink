"use client";

import { plainText } from "@/lib/formatting";
import { useRouter } from "next/navigation";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { GroupInfo } from "@/lib/groups";
import type { ConversationOut, GroupInviteRef, MessageOut, PersonCard, ReplyRef } from "@/lib/messages";
import { ChatHeader, SelectionBar, type SelectionAction } from "./chat/ChatHeader";
import { ChatIcon } from "./chat/ChatIcon";
import { exportChat, logStamp } from "./chat/exportChat";
import { ForwardDialog } from "./chat/ForwardDialog";
import { inviteOpen } from "./chat/InviteCard";
import { MessageInfoDialog } from "./chat/MessageInfoDialog";
import { Modal } from "./chat/Modal";
import { Icon } from "./Icon";
import { MessageComposer, post, type Banner } from "./MessageComposer";
import { Bubble, batched, snippet } from "./MessageList";

const dayOf = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });

/** Layout work before the first paint in the browser; nothing on the server (where it would only warn). */
const useBrowserLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

/** Your own messages can be edited or deleted for 2 hours after sending (the server holds the same line). */
const EDIT_WINDOW_MS = 2 * 60 * 60 * 1000;
const changeable = (m: MessageOut) => m.mine && !m.deleted && Date.now() - Date.parse(m.createdAt) < EDIT_WINDOW_MS;

/** How long a press has to be held to select a message. */
const LONG_PRESS_MS = 450;

/** A message as a quote: what the composer shows while answering it. */
const asRef = (m: MessageOut): ReplyRef => ({
  id: m.id,
  senderName: m.sender?.name ?? "Unknown",
  mine: m.mine,
  body: m.body,
  fileName: m.file?.name ?? null,
  fileMime: m.file?.mime ?? null,
  fileDocument: Boolean(m.file?.document),
  deleted: m.deleted,
});

/** What copying a message puts on the clipboard: its words, or what it carried. */
// Copying gives the words, as in WhatsApp; forwarding keeps the formatting.
const copyText = (m: MessageOut) => plainText(m.body).trim() || snippet(asRef(m));

/**
 * Something that can be selected: not a line about the group, not a deleted
 * message, and an invitation only by whoever sent it (to see who it reached).
 */
const selectable = (m: MessageOut) => !m.deleted && !m.event && (!m.groupInvite || m.mine);

/**
 * A private chat or a group, as the app has it: bubbles with day separators,
 * newest at the bottom, the composer under them. Hold a message (or
 * right-click it) to select it; the header then offers what can be done
 * with the selection. A quoted reply jumps to the original.
 */
export function ChatView({
  conversationId,
  initial,
  other,
  group: initialGroup,
  myName,
}: {
  conversationId: string;
  initial: MessageOut[];
  other: PersonCard | null;
  group: GroupInfo | null;
  myName: string;
}) {
  const router = useRouter();
  const [messages, setMessages] = useState(initial);
  const [group, setGroup] = useState(initialGroup);
  const [selected, setSelected] = useState<string[]>([]);
  const [replyTo, setReplyTo] = useState<MessageOut | null>(null);
  const [editing, setEditing] = useState<MessageOut | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<MessageOut[] | null>(null);
  const [forwarding, setForwarding] = useState(false);
  const [infoFor, setInfoFor] = useState<MessageOut | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [hitAt, setHitAt] = useState(0);
  const [exporting, setExporting] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const scroller = useRef<HTMLDivElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const press = useRef<{ timer: number; x: number; y: number } | null>(null);
  const longPressed = useRef(false);
  const toastTimer = useRef<number>();

  const showToast = (text: string) => {
    setToast(text);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2_500);
  };

  const reload = async () => {
    try {
      const r = await api<{ messages: MessageOut[]; group: GroupInfo | null }>(`/api/conversations/${conversationId}`);
      setMessages(r.messages);
      if (r.group) setGroup(r.group);
    } catch {
      // Next time.
    }
  };

  // A quick look every few seconds while the chat is on screen (new messages, ticks), and at once when a push arrives.
  useEffect(() => {
    const look = () => document.visibilityState === "visible" && reload();
    const timer = setInterval(look, 4_000);
    window.addEventListener("wink:push", reload);
    document.addEventListener("visibilitychange", look);
    return () => {
      clearInterval(timer);
      window.removeEventListener("wink:push", reload);
      document.removeEventListener("visibilitychange", look);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  // The chat opens on its newest message: placed at the bottom before the first paint, shown only then.
  useBrowserLayoutEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
    setReady(true);
  }, []);

  // While you are at the bottom you stay there, as pictures load and the composer grows.
  useEffect(() => {
    const el = scroller.current;
    const inner = content.current;
    if (!el || !inner) return;
    const stick = () => {
      if (atBottom.current) el.scrollTop = el.scrollHeight;
    };
    const observer = new ResizeObserver(stick);
    observer.observe(inner);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // Something new comes into view when you are at the bottom or it is yours; otherwise it waits below.
  const newest = messages[messages.length - 1];
  const lastNewest = useRef(newest?.id);
  useBrowserLayoutEffect(() => {
    const el = scroller.current;
    if (!el || !newest || newest.id === lastNewest.current) return;
    lastNewest.current = newest.id;
    if (newest.mine || atBottom.current) el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
  }, [newest?.id]);

  const byId = new Map(messages.map((m) => [m.id, m]));
  // Oldest first, as they sit in the chat.
  const chosen = messages.filter((m) => selected.includes(m.id) && selectable(m));
  const selecting = chosen.length > 0;

  // A selected message that went away (archived, deleted elsewhere) leaves the selection.
  useEffect(() => {
    if (selected.length > 0 && chosen.length !== selected.length) setSelected(chosen.map((m) => m.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || document.querySelector("[role=dialog]")) return;
      if (selected.length > 0) setSelected([]);
      else if (searchOpen) setSearchOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selected.length, searchOpen]);

  /** Select or unselect a bubble: a batch of photos goes as one, all its messages together. */
  const toggle = (run: MessageOut[]) => {
    const ids = run.filter(selectable).map((m) => m.id);
    if (ids.length === 0) return;
    setActionError(null);
    setSelected((s) => (ids.every((id) => s.includes(id)) ? s.filter((id) => !ids.includes(id)) : [...s, ...ids.filter((id) => !s.includes(id))]));
  };

  /** Scroll to a message and light it up for a second. */
  const jump = (id: string) => {
    const el = document.getElementById(`m-${id}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setFlash(id);
    setTimeout(() => setFlash((f) => (f === id ? null : f)), 1000);
  };

  /* ── Selecting: hold (or right-click) a message; while any is selected, a click adds or removes one. ── */

  const cancelPress = () => {
    if (press.current) window.clearTimeout(press.current.timer);
    press.current = null;
  };

  const pressOn = (run: MessageOut[]): React.HTMLAttributes<HTMLDivElement> =>
    run.some(selectable)
      ? {
          onPointerDown: (e) => {
            longPressed.current = false;
            if (e.button !== 0) return;
            cancelPress();
            const timer = window.setTimeout(() => {
              press.current = null;
              longPressed.current = true;
              navigator.vibrate?.(15);
              toggle(run);
            }, LONG_PRESS_MS);
            press.current = { timer, x: e.clientX, y: e.clientY };
          },
          onPointerMove: (e) => {
            // A scroll or a drag is not a hold.
            if (press.current && Math.hypot(e.clientX - press.current.x, e.clientY - press.current.y) > 8) cancelPress();
          },
          onPointerUp: cancelPress,
          onPointerLeave: cancelPress,
          onPointerCancel: cancelPress,
          onContextMenu: (e) => {
            e.preventDefault();
            // A phone fires this at the end of a hold that has already selected it.
            if (!longPressed.current) toggle(run);
          },
          onClickCapture: (e) => {
            // The click that ends a hold, and any click while selecting, is about selection, not the picture or link under it.
            if (longPressed.current || selecting) {
              e.preventDefault();
              e.stopPropagation();
              if (!longPressed.current) toggle(run);
              longPressed.current = false;
            }
          },
        }
      : {};

  /* ── What the selection bar offers. ── */

  const one = chosen.length === 1 ? chosen[0] : null;
  /** Do something with the selection, which then ends. */
  const pick = (act: () => void) => {
    act();
    setSelected([]);
  };
  const actions: SelectionAction[] = [];
  if (selecting) {
    // An invitation card has only its info: it is not text to copy, forward, edit or reply to.
    const invite = chosen.some((m) => m.groupInvite);
    const allMine = chosen.every((m) => m.mine);
    const allRecent = chosen.every(changeable);
    if (one?.mine) actions.push({ label: "Info", icon: "info", onClick: () => pick(() => setInfoFor(one)) });
    if (!invite) {
      if (one) actions.push({ label: "Reply", icon: "reply", onClick: () => pick(() => (setEditing(null), setReplyTo(one))) });
      if (one?.mine) actions.push({ label: "Edit", icon: "edit", enabled: allRecent, onClick: () => pick(() => (setReplyTo(null), setEditing(one))) });
      actions.push({ label: "Copy", icon: "copy", onClick: () => copy() });
      if (allMine) actions.push({ label: "Delete", icon: "trash", enabled: allRecent, onClick: () => setDeleting(chosen) });
      actions.push({ label: "Forward", icon: "forward", onClick: () => setForwarding(true) });
    }
  }

  /** One message: its words alone. Several: each with its time and who said it, the way WhatsApp does. */
  const copy = () => {
    const text = one
      ? copyText(one)
      : chosen.map((m) => `[${logStamp(m.createdAt)}] ${m.mine ? myName : m.sender?.name ?? "Unknown"}: ${copyText(m)}`).join("\n");
    const n = chosen.length;
    setSelected([]);
    navigator.clipboard
      .writeText(text)
      .then(() => showToast(n === 1 ? "Copied" : `${n} messages copied`))
      .catch(() => showToast("Could not copy"));
  };

  /** Gone at once on screen; the server is told one by one behind it. */
  const remove = async (list: MessageOut[]) => {
    setDeleting(null);
    setSelected([]);
    const ids = new Set(list.map((m) => m.id));
    if (editing && ids.has(editing.id)) setEditing(null);
    if (replyTo && ids.has(replyTo.id)) setReplyTo(null);
    setMessages((ms) => ms.map((m) => (ids.has(m.id) ? { ...m, body: "", file: null, deleted: true } : m)));
    let failed = 0;
    for (const m of list) await api(`/api/messages/${m.id}`, { method: "DELETE" }).catch(() => failed++);
    if (failed > 0) setActionError(failed === 1 ? "One message could not be deleted." : `${failed} messages could not be deleted.`);
    await reload();
  };

  /** Each chosen message (oldest first) into each chosen chat, in the background. */
  const forward = (targets: ConversationOut[]) => {
    const list = chosen;
    setForwarding(false);
    setSelected([]);
    showToast(targets.length === 1 ? `Forwarding to ${targets[0].other.name}` : `Forwarding to ${targets.length} chats`);
    void (async () => {
      const failed = new Set<string>();
      for (const c of targets) {
        for (const m of list) {
          await api(`/api/conversations/${c.id}`, { method: "POST", json: { forwardOf: m.id } }).catch(() => failed.add(c.other.name));
        }
      }
      showToast(failed.size ? `Could not forward to ${[...failed].join(", ")}` : "Forwarded");
      if (targets.some((c) => c.id === conversationId)) await reload();
    })();
  };

  /** Join or decline a group from its invitation; joining opens the group. */
  const answerInvite = async (inv: GroupInviteRef, accept: boolean) => {
    await api(`/api/groups/invites/${inv.id}`, { method: "POST", json: { accept } });
    if (accept) {
      showToast(`You joined ${inv.groupName}`);
      router.push(`/chats/${inv.groupId}`);
      router.refresh();
    } else {
      await reload();
    }
  };

  /* ── Search: matches lit up, stepped through from the newest. ── */

  const needle = searchOpen ? query.trim() : "";
  const hits = needle
    ? messages.filter((m) => !m.deleted && !m.event && !m.groupInvite && m.body.toLowerCase().includes(needle.toLowerCase())).map((m) => m.id)
    : [];
  const hitsKey = hits.join(",");
  useEffect(() => {
    setHitAt(Math.max(0, hits.length - 1));
    if (hits.length > 0) jump(hits[hits.length - 1]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hitsKey]);
  const step = (by: number) => {
    const next = hitAt + by;
    if (next < 0 || next >= hits.length) return;
    setHitAt(next);
    jump(hits[next]);
  };

  /* ── Export: always a private chat; a group only by its admins. ── */

  const canExport = !group || group.myRole === "admin";
  const title = other?.name ?? group?.name ?? "Chat";
  const runExport = async () => {
    setActionError(null);
    setExporting("Fetching the chat…");
    try {
      const who = other
        ? { name: other.name, roleLabel: other.roleLabel }
        : { name: title, roleLabel: `Group · ${group?.members.length ?? 0} member${group?.members.length === 1 ? "" : "s"}` };
      await exportChat(conversationId, who, myName, setExporting);
      showToast("Chat exported");
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not export.");
    } finally {
      setExporting(null);
    }
  };

  const rows: React.ReactNode[] = [];
  let lastDay = "";
  // A batch of photos (sent or forwarded together) is one bubble with a grid.
  for (const { shown: m, run } of batched(messages)) {
    const day = dayOf(run[0].createdAt);
    if (day !== lastDay) {
      lastDay = day;
      rows.push(
        <div key={`day-${day}`} className="my-2 flex justify-center">
          <span className="chip text-snow-faint">{day}</span>
        </div>,
      );
    }
    // A line about the group itself (joined, left, …): not something anyone said.
    if (m.event) {
      rows.push(
        <div key={m.id} id={`m-${m.id}`} className="my-1.5 flex justify-center">
          <span className="max-w-[85%] rounded-full bg-night px-3 py-1 text-center text-xs text-snow-soft">{m.event}</span>
        </div>,
      );
      continue;
    }
    // A quote reads as the original does now, when it is loaded here.
    const original = m.replyTo ? byId.get(m.replyTo.id) : undefined;
    const inv = m.groupInvite;
    rows.push(
      <Bubble
        key={m.id}
        m={m}
        quote={original ? asRef(original) : m.replyTo}
        onQuote={jump}
        flash={run.some((x) => x.id === flash)}
        selected={run.some((x) => selected.includes(x.id))}
        senderName={group && !m.mine ? m.sender?.name ?? null : null}
        highlight={needle && run.some((x) => hits.includes(x.id)) ? needle : null}
        onInvite={inv && !m.mine && inviteOpen(inv) ? (accept) => answerInvite(inv, accept) : undefined}
        press={pressOn(run)}
      >
        {/* The batch's other messages, so a quote of any of them still finds this bubble. */}
        {run.filter((x) => x.id !== m.id).map((x) => <span key={x.id} id={`m-${x.id}`} />)}
      </Bubble>,
    );
  }

  const banner: Banner | null = editing
    ? { title: "Edit message", text: snippet(asRef(editing)), onCancel: () => setEditing(null) }
    : replyTo
      ? { title: `Replying to ${replyTo.mine ? "yourself" : replyTo.sender?.name ?? "message"}`, text: snippet(asRef(replyTo)), onCancel: () => setReplyTo(null) }
      : null;

  return (
    <>
      {selecting ? (
        <SelectionBar count={chosen.length} onClose={() => setSelected([])} actions={actions} />
      ) : (
        <ChatHeader
          title={title}
          subtitle={other ? other.roleLabel : `${group?.members.length ?? 0} member${group?.members.length === 1 ? "" : "s"}`}
          photoUrl={other?.photoUrl ?? group?.photoUrl ?? null}
          href={`/chats/${conversationId}/${other ? "profile" : "group"}`}
          canExport={canExport}
          onSearch={() => setSearchOpen(true)}
          onExport={runExport}
        />
      )}
      {searchOpen && (
        <div className="flex shrink-0 items-center gap-1 border-b border-night-line px-2 py-2">
          <div className="relative min-w-0 flex-1">
            <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
            <input
              className="input py-2 pl-9"
              placeholder="Search messages"
              value={query}
              autoFocus
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") step(e.shiftKey ? 1 : -1);
                if (e.key === "Escape") setSearchOpen(false);
              }}
            />
          </div>
          <span className="w-12 shrink-0 text-center text-xs text-snow-soft">{needle ? (hits.length ? `${hitAt + 1}/${hits.length}` : "0") : ""}</span>
          <button type="button" className="btn-icon" title="Older match" aria-label="Older match" disabled={hitAt <= 0} onClick={() => step(-1)}>
            <ChatIcon name="up" className="h-5 w-5" />
          </button>
          <button type="button" className="btn-icon" title="Newer match" aria-label="Newer match" disabled={hitAt >= hits.length - 1} onClick={() => step(1)}>
            <ChatIcon name="down" className="h-5 w-5" />
          </button>
          <button
            type="button"
            className="btn-icon"
            title="Close search"
            aria-label="Close search"
            onClick={() => {
              setSearchOpen(false);
              setQuery("");
            }}
          >
            <Icon name="close" className="h-5 w-5" />
          </button>
        </div>
      )}
      <div
        ref={scroller}
        className={`min-h-0 flex-1 overflow-y-auto px-3 py-3 sm:px-4 ${ready ? "" : "invisible"}`}
        onScroll={(e) => {
          const el = e.currentTarget;
          atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
        }}
      >
        <div ref={content} className={`space-y-1 ${selecting ? "select-none" : ""}`}>
          {messages.length === 0 && <p className="py-8 text-center text-sm text-snow-faint">No messages yet. Say hello.</p>}
          {rows}
        </div>
      </div>
      {actionError && (
        <div className="shrink-0 px-3 pt-2">
          <p className="error">{actionError}</p>
        </div>
      )}
      {group && !group.canSend ? (
        <p className="shrink-0 border-t border-night-line p-4 text-center text-sm text-snow-faint">Only the group&apos;s admins can send here.</p>
      ) : (
        <div className="shrink-0 border-t border-night-line p-2 sm:p-3">
          <MessageComposer
            placeholder="Message"
            banner={banner}
            editText={editing ? editing.body : null}
            send={async (draft) => {
              if (editing) {
                await api(`/api/messages/${editing.id}`, { method: "PATCH", json: { body: draft.body } });
                setEditing(null);
              } else {
                await post(`/api/conversations/${conversationId}`, { ...draft, ...(replyTo ? { replyToId: replyTo.id } : {}) });
                setReplyTo(null);
              }
              await reload();
            }}
          />
        </div>
      )}

      {toast && (
        <div className="pointer-events-none absolute inset-x-0 bottom-24 z-30 flex justify-center px-4">
          <span className="rounded-full border border-night-line bg-night px-4 py-2 text-sm text-snow shadow-card">{toast}</span>
        </div>
      )}
      {deleting && (
        <Modal onClose={() => setDeleting(null)}>
          <p className="font-semibold">{deleting.length === 1 ? "Delete message?" : `Delete ${deleting.length} messages?`}</p>
          <p className="mt-1 text-sm text-snow-soft">{deleting.length === 1 ? "It will be deleted for both of you." : "They will be deleted for both of you."}</p>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className="btn-ghost" onClick={() => setDeleting(null)}>
              Cancel
            </button>
            <button type="button" className="btn-danger" onClick={() => remove(deleting)}>
              Delete
            </button>
          </div>
        </Modal>
      )}
      {forwarding && <ForwardDialog count={chosen.length} onClose={() => setForwarding(false)} onSend={forward} />}
      {infoFor && <MessageInfoDialog messageId={infoFor.id} preview={snippet(asRef(infoFor))} onClose={() => setInfoFor(null)} />}
      {exporting && (
        <Modal>
          <p className="font-semibold">Exporting chat</p>
          <div className="mt-3 flex items-center gap-3 text-sm text-snow-soft">
            <span className="h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-gold border-t-transparent" />
            {exporting}
          </div>
        </Modal>
      )}
    </>
  );
}
