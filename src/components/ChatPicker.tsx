"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { PublicUser } from "@/lib/users";
import { Avatar } from "./Avatar";

/** Search the people you may chat with; tapping one opens (or finds) the chat. */
export function ChatPicker({ people }: { people: PublicUser[] }) {
  const router = useRouter();
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const shown = people.filter(
    (p) => !filter || `${p.name ?? ""} ${p.email} ${p.teamName ?? ""} ${p.roleLabel}`.toLowerCase().includes(filter.toLowerCase()),
  );

  const open = async (id: string) => {
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ id: string }>("/api/conversations", { method: "POST", json: { memberId: id } });
      router.push(`/chats/${r.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open the chat.");
      setBusy(false);
    }
  };

  return (
    <div className="card space-y-3">
      {error && <p className="error">{error}</p>}
      <input className="input" placeholder="Search by name, team or role" value={filter} onChange={(e) => setFilter(e.target.value)} autoFocus />
      <div className="max-h-[70vh] divide-y divide-night-line overflow-y-auto">
        {shown.map((p) => (
          <button key={p.id} className="row w-full text-left" onClick={() => open(p.id)} disabled={busy}>
            <Avatar src={p.photoUrl} name={p.name ?? p.email} size={40} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{p.name ?? p.email}</span>
              <span className="block truncate text-xs text-snow-faint">
                {p.roleLabel}
                {p.teamName ? ` · ${p.teamName}` : ""}
              </span>
            </span>
          </button>
        ))}
        {shown.length === 0 && <p className="py-3 text-sm text-snow-faint">No one matches.</p>}
      </div>
    </div>
  );
}
