import { VerifyCard } from "@/components/VerifyCard";
import { userColumns, type SessionUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { ROLE_LABEL } from "@/lib/roles";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Verify" };

/** A scanned QR opened in the browser: the same card the in-app scanner shows. */
export default async function Verify({ params }: { params: Promise<{ token: string }> }) {
  await requireProfile();
  const { token } = await params;
  const user = await one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE qr_token = $1`, [token.slice(0, 100)]);

  return (
    <div className="space-y-4">
      <h1 className="text-lg font-semibold">Verification</h1>
      {user ? (
        <VerifyCard
          v={{
            id: user.id,
            name: user.name,
            roleLabel: ROLE_LABEL[user.role],
            teamName: user.team_name,
            status: user.status,
            verifyCode: user.verify_code,
            photoUrl: user.photo_key ? `/api/users/${user.id}/photo` : null,
            profileComplete: Boolean(user.profile_completed_at),
          }}
        />
      ) : (
        <p className="error">No account matches this code.</p>
      )}
    </div>
  );
}
