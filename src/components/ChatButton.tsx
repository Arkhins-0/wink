"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";

/** Opens (or finds) the private chat with someone; the server applies the chat rule. */
export function ChatButton({ userId, name }: { userId: string; name: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="space-y-2">
      <button
        className="btn-gold w-full sm:w-auto"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            const r = await api<{ id: string }>("/api/conversations", { method: "POST", json: { memberId: userId } });
            router.push(`/chats/${r.id}`);
          } catch (e) {
            setError(e instanceof Error ? e.message : "Could not open a chat.");
            setBusy(false);
          }
        }}
      >
        {busy ? "Opening chat…" : `Chat with ${name}`}
      </button>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
