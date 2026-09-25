"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { ROLE_LABEL, ROLES, type Role } from "@/lib/roles";
import { MessageComposer } from "./MessageComposer";

/** The coordinator's relay to volunteers/security, or the admin's mail to ticked roles. */
export function EmailForm({ mode, group }: { mode: "relay"; group: "volunteers" | "security" } | { mode: "bulk"; group?: undefined }) {
  const router = useRouter();
  const [roles, setRoles] = useState<Set<Role>>(new Set(ROLES));
  const [sent, setSent] = useState<number | null>(null);

  if (sent !== null)
    return (
      <div className="card space-y-3 text-sm">
        <p>Sent to {sent} {sent === 1 ? "person" : "people"}.</p>
        <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => router.push("/people")}>
          Back to people
        </button>
      </div>
    );

  return (
    <div className="space-y-4">
      {mode === "bulk" && (
        <div className="card flex flex-wrap gap-2">
          {ROLES.map((r) => (
            <label key={r} className={`chip cursor-pointer ${roles.has(r) ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`}>
              <input
                type="checkbox"
                className="hidden"
                checked={roles.has(r)}
                onChange={() =>
                  setRoles((s) => {
                    const n = new Set(s);
                    if (n.has(r)) n.delete(r);
                    else n.add(r);
                    return n;
                  })
                }
              />
              {ROLE_LABEL[r]}
            </label>
          ))}
        </div>
      )}
      <MessageComposer
        urgentOption={false}
        linkPreviews={false}
        submitLabel="Send email"
        placeholder="What everyone needs to know"
        send={async (draft) => {
          const r =
            mode === "relay"
              ? await api<{ delivered: number }>("/api/email/relay", { method: "POST", json: { group, body: draft.body, fileId: draft.fileId } })
              : await api<{ delivered: number }>("/api/email/bulk", { method: "POST", json: { roles: Array.from(roles), body: draft.body, fileId: draft.fileId } });
          setSent(r.delivered);
        }}
      />
    </div>
  );
}
