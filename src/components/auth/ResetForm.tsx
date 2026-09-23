"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { PasswordForm } from "./PasswordForm";

export function ResetForm({ token }: { token: string }) {
  const [email, setEmail] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    api<{ email: string }>(`/api/auth/reset/${token}`).then((r) => setEmail(r.email)).catch((e) => setError(e.message));
  }, [token]);

  if (error) return <p className="error">{error}</p>;
  if (done)
    return (
      <div className="space-y-4 text-sm text-snow-soft">
        <p>Your password is changed. Sign in with it on every device.</p>
        <Link href="/" className="btn-gold w-full">
          Sign in
        </Link>
      </div>
    );
  if (!email) return <p className="text-sm text-snow-faint">Checking your link…</p>;

  return (
    <div className="space-y-5">
      <p className="text-sm text-snow-soft">{email}</p>
      <PasswordForm
        onSubmit={async (password) => {
          await api(`/api/auth/reset/${token}`, { method: "POST", json: { password } });
          setDone(true);
        }}
      />
    </div>
  );
}
