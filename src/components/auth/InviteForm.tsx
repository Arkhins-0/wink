"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { PasswordForm } from "./PasswordForm";

export function InviteForm({ token }: { token: string }) {
  const router = useRouter();
  const [info, setInfo] = useState<{ email: string; roleLabel: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<{ email: string; roleLabel: string }>(`/api/auth/invite/${token}`).then(setInfo).catch((e) => setError(e.message));
  }, [token]);

  if (error) return <p className="error">{error}</p>;
  if (!info) return <p className="text-sm text-snow-faint">Checking your link…</p>;

  return (
    <div className="space-y-5">
      <p className="text-sm text-snow-soft">
        <span className="font-semibold text-snow">{info.email}</span> · {info.roleLabel}
      </p>
      <PasswordForm
        label="Create account"
        agreement
        onSubmit={async (password) => {
          await api(`/api/auth/invite/${token}`, { method: "POST", json: { password, acceptTerms: true } });
          router.replace("/onboarding");
          router.refresh();
        }}
      />
    </div>
  );
}
