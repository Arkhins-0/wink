import { redirect } from "next/navigation";
import { AuthCard } from "@/components/AuthCard";
import { SignInForm } from "@/components/auth/SignInForm";
import { currentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function SignIn({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const user = await currentUser();
  if (user) redirect(user.profile_completed_at ? "/home" : "/onboarding");
  const { next } = await searchParams;
  return (
    <AuthCard title="Sign in">
      <SignInForm next={next} />
    </AuthCard>
  );
}
