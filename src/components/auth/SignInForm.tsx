"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";

export function SignInForm({ next }: { next?: string }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/login", { method: "POST", json: { email, password } });
      router.replace(next && next.startsWith("/") ? next : "/home");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not sign in.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <p className="error">{error}</p>}
      <div>
        <label className="label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div>
        <label className="label" htmlFor="password">
          Password
        </label>
        <input
          id="password"
          className="input"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Signing in…" : "Sign in"}
      </button>
      <div className="flex justify-between text-xs text-snow-faint">
        <Link href="/forgot" className="hover:text-snow">
          Forgot password
        </Link>
        <a href="/download" className="hover:text-snow">
          Open the Android app
        </a>
      </div>
    </form>
  );
}
