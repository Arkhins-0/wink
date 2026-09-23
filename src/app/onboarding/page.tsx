import { redirect } from "next/navigation";
import { AuthCard } from "@/components/AuthCard";
import { OnboardingForm } from "@/components/auth/OnboardingForm";
import { requireSession } from "@/lib/session";

export const dynamic = "force-dynamic";
export const metadata = { title: "Your profile" };

export default async function Onboarding() {
  const user = await requireSession();
  if (user.profile_completed_at) redirect("/home");
  return (
    <AuthCard title="Your profile">
      <OnboardingForm />
    </AuthCard>
  );
}
