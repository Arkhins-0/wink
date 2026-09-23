"use client";

import { useState } from "react";

/** Choose a password, twice. `onSubmit` does the call; errors come back as a message. */
export function PasswordForm({ onSubmit, label = "Save password" }: { onSubmit: (password: string) => Promise<void>; label?: string }) {
  const [password, setPassword] = useState("");
  const [again, setAgain] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== again) return setError("The passwords do not match.");
    setBusy(true);
    setError(null);
    try {
      await onSubmit(password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <p className="error">{error}</p>}
      <div>
        <label className="label" htmlFor="pw">
          New password
        </label>
        <input id="pw" className="input" type="password" autoComplete="new-password" minLength={8} required value={password} onChange={(e) => setPassword(e.target.value)} />
        <p className="mt-1 text-xs text-snow-faint">At least 8 characters.</p>
      </div>
      <div>
        <label className="label" htmlFor="pw2">
          Repeat password
        </label>
        <input id="pw2" className="input" type="password" autoComplete="new-password" required value={again} onChange={(e) => setAgain(e.target.value)} />
      </div>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Saving…" : label}
      </button>
    </form>
  );
}
