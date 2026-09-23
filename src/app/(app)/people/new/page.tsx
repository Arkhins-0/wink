import { notFound } from "next/navigation";
import { NewPersonForm } from "@/components/NewPersonForm";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Add person" };

export default async function NewPerson() {
  const user = await requireProfile();
  const roles = CREATE_RULES[user.role] ?? [];
  if (roles.length === 0) notFound();
  return (
    <div className="space-y-4">
      <h1 className="page-title">Add person</h1>
      <NewPersonForm roles={roles} teamName={user.team_name} />
    </div>
  );
}
