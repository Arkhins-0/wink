import { notFound } from "next/navigation";
import { EmailForm } from "@/components/EmailForm";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Email" };

export default async function EmailPage({ searchParams }: { searchParams: Promise<{ group?: string }> }) {
  const user = await requireProfile();
  const { group } = await searchParams;
  if (user.role === "coordinator") {
    if (group !== "volunteers" && group !== "security") notFound();
    return (
      <div className="space-y-4">
        <h1 className="text-lg font-semibold">Email my {group}</h1>
        <p className="text-sm text-snow-soft">
          Goes by email and as an urgent message to the {group} {group === "volunteers" ? "assigned to you" : "under you"}.
        </p>
        <EmailForm mode="relay" group={group} />
      </div>
    );
  }
  if (user.role === "admin") {
    return (
      <div className="space-y-4">
        <h1 className="text-lg font-semibold">Email everyone</h1>
        <p className="text-sm text-snow-soft">Tick the groups. Every active person in them gets the email and an urgent message.</p>
        <EmailForm mode="bulk" />
      </div>
    );
  }
  notFound();
}
