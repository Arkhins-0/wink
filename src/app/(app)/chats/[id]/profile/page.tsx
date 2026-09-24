import Link from "next/link";
import { notFound } from "next/navigation";
import QRCode from "qrcode";
import { Icon } from "@/components/Icon";
import { VerifyCard } from "@/components/VerifyCard";
import { canRead, conversationById, photoUrl } from "@/lib/messages";
import { ROLE_LABEL } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { qrUrl, userById } from "@/lib/users";

export const metadata = { title: "Profile" };

/**
 * The other person in a private chat as their ID card: what a scan shows,
 * and their QR. Email, phone and date of birth stay private.
 */
export default async function ChatProfile({ params }: { params: Promise<{ id: string }> }) {
  const me = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const conv = await conversationById(id);
  if (!conv || conv.kind !== "direct" || !canRead(me, conv)) notFound();
  const user = await userById(conv.owner_id === me.id ? conv.member_id! : conv.owner_id!);
  if (!user) notFound();
  const svg = await QRCode.toString(qrUrl(user), { type: "svg", margin: 1, color: { dark: "#0B0B0C", light: "#FFFFFF" } });

  return (
    <div className="h-full min-h-0 space-y-4 overflow-y-auto pb-4">
      <div className="flex items-center gap-2">
        <Link href={`/chats/${id}`} className="btn-icon" aria-label="Back to the chat">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <h1 className="text-lg font-semibold">Profile</h1>
      </div>
      <section className="card space-y-5">
        <VerifyCard
          chat={false}
          v={{
            id: user.id,
            name: user.name,
            roleLabel: ROLE_LABEL[user.role],
            teamName: user.team_name,
            status: user.status,
            verifyCode: user.verify_code,
            photoUrl: photoUrl(user),
            profileComplete: Boolean(user.profile_completed_at),
          }}
        />
        <div className="mx-auto w-48 rounded-xl bg-white p-2" dangerouslySetInnerHTML={{ __html: svg }} />
        <p className="text-center text-xs text-snow-faint">Scan to check who this is.</p>
      </section>
      <Link href={`/chats/${id}`} className="btn-gold w-full">
        Open chat
      </Link>
    </div>
  );
}
