import { AuthCard } from "@/components/AuthCard";
import { ResetForm } from "@/components/auth/ResetForm";

export const metadata = { title: "Reset password" };

export default async function Reset({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <AuthCard title="Choose a new password">
      <ResetForm token={token} />
    </AuthCard>
  );
}
