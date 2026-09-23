import { AuthCard } from "@/components/AuthCard";
import { InviteForm } from "@/components/auth/InviteForm";

export const metadata = { title: "Set up your account" };

export default async function Invite({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthCard title="Set up your account">
      <InviteForm token={token} />
    </AuthCard>
  );
}
