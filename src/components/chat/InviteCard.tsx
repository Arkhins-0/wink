"use client";

import { useState } from "react";
import type { GroupInviteRef } from "@/lib/messages";

/** Still waiting for an answer, and not past its two days. */
export const inviteOpen = (inv: GroupInviteRef) => inv.status === "pending" && Date.parse(inv.expiresAt) > Date.now();

/** A group invitation inside its bubble: the group's name, and Join / Decline for the person invited. */
export function InviteCard({ inv, mine, onAnswer }: { inv: GroupInviteRef; mine: boolean; onAnswer?: (accept: boolean) => Promise<void> }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const open = inviteOpen(inv);
  const onDark = !mine;
  const note = `text-xs font-medium ${onDark ? "text-snow-soft" : "text-night/70"}`;

  const answer = async (accept: boolean) => {
    if (!onAnswer) return;
    setBusy(true);
    setError(null);
    try {
      await onAnswer(accept);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not answer the invitation.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`min-w-[200px] rounded-xl p-2.5 ${onDark ? "bg-night" : "bg-night/10"}`}>
      <p className={`text-[10px] font-semibold uppercase tracking-wide ${onDark ? "text-gold" : "text-night/60"}`}>{inv.upward ? "Join request" : "Group invitation"}</p>
      <p className={`text-base font-semibold ${onDark ? "text-snow" : "text-night"}`}>{inv.groupName}</p>
      <div className="mt-1.5">
        {inv.status === "accepted" ? (
          <p className={note}>Joined</p>
        ) : inv.status === "declined" ? (
          <p className={note}>Declined</p>
        ) : inv.status === "revoked" ? (
          <p className={note}>Revoked</p>
        ) : !open ? (
          <p className={note}>Expired · good for 2 days</p>
        ) : mine || !onAnswer ? (
          <p className={note}>{mine ? "Waiting for an answer" : "Open"}</p>
        ) : (
          <div className="flex gap-2">
            <button type="button" className="btn-gold px-4 py-1.5 text-xs" disabled={busy} onClick={() => answer(true)}>
              Join
            </button>
            <button type="button" className="btn-ghost px-4 py-1.5 text-xs" disabled={busy} onClick={() => answer(false)}>
              Decline
            </button>
          </div>
        )}
        {error && <p className="mt-1.5 text-xs text-danger">{error}</p>}
      </div>
    </div>
  );
}
