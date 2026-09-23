"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { ROLE_LABEL, type Role } from "@/lib/roles";

/** An email and a role. The invite goes out at once. */
export function NewPersonForm({ roles, teamName }: { roles: Role[]; teamName: string | null }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>(roles[0]);
  const [team, setTeam] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ user: { id: string } }>("/api/users", { method: "POST", json: { email, role, teamName: team } });
      router.push(`/people/${r.user.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="card max-w-md space-y-4">
      {error && <p className="error">{error}</p>}
      <div>
        <label className="label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div>
        <span className="label">Role</span>
        <div className="flex flex-wrap gap-2">
          {roles.map((r) => (
            <button
              key={r}
              type="button"
              className={`chip ${role === r ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`}
              onClick={() => setRole(r)}
            >
              {ROLE_LABEL[r]}
            </button>
          ))}
        </div>
      </div>
      {role === "team_manager" && (
        <div>
          <label className="label" htmlFor="team">
            Team
          </label>
          <input id="team" className="input" required value={team} onChange={(e) => setTeam(e.target.value)} />
        </div>
      )}
      {(role === "driver" || role === "crew") && teamName && <p className="text-xs text-snow-faint">Team: {teamName}</p>}
      <p className="text-xs text-snow-faint">They get an email with a link to choose a password and fill in their profile.</p>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Sending invite…" : "Create and send invite"}
      </button>
    </form>
  );
}
