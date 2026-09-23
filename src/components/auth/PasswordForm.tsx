"use client";

import Link from "next/link";
import { useState } from "react";

/**
 * Choose a password, twice. `onSubmit` does the call; errors come back as a
 * message. With `agreement`, a box to agree to the Terms and the Privacy
 * Policy sits above the button, which stays off until it is ticked.
 */
export function PasswordForm({
  onSubmit,
  label = "Save password",
  agreement = false,
}: {
  onSubmit: (password: string) => Promise<void>;
  label?: string;
  agreement?: boolean;
}) {
  const [agreed, setAgreed] = useState(false);
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
      {agreement && (
        <label className="flex cursor-pointer items-start gap-3 text-sm text-snow-soft">
          <input type="checkbox" className="mt-0.5 h-4 w-4 shrink-0 accent-gold" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
          <span>
            I agree to the{" "}
            <Link href="/terms" target="_blank" className="text-gold hover:underline">
              Terms and Conditions
            </Link>{" "}
            and the{" "}
            <Link href="/privacy" target="_blank" className="text-gold hover:underline">
              Privacy Policy
            </Link>
            .
          </span>
        </label>
      )}
      <button className="btn-gold w-full" disabled={busy || (agreement && !agreed)}>
        {busy ? "Saving…" : label}
      </button>
    </form>
  );
}
