"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/client";

export function ForgotForm() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    await api("/api/auth/forgot", { method: "POST", json: { email } }).catch(() => null);
    setSent(true);
    setBusy(false);
  };

  if (sent) {
    return (
      <div className="space-y-4 text-sm text-snow-soft">
        <p>If that email has an account, a link to choose a new password is on its way. It works for 2 hours.</p>
        <Link href="/" className="btn-ghost w-full">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label className="label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Sending…" : "Email me a link"}
      </button>
      <Link href="/" className="block text-center text-xs text-snow-faint hover:text-snow">
        Back to sign in
      </Link>
    </form>
  );
}
