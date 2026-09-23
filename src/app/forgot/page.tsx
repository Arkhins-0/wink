import { AuthCard } from "@/components/AuthCard";
import { ForgotForm } from "@/components/auth/ForgotForm";

export const metadata = { title: "Forgot password" };

export default function Forgot() {
  return (
    <AuthCard title="Forgot password">
      <ForgotForm />
    </AuthCard>
  );
}
