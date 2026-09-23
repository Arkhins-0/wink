import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { StatusBadge } from "@/components/StatusBadge";
import { descendants } from "@/lib/hierarchy";
import { CREATE_RULES, ROLE_LABEL, ROLES, type Role } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "People" };

export default async function People() {
  const user = await requireProfile();
  const people = (await descendants(user)).map(toPublic);
  const canCreate = (CREATE_RULES[user.role] ?? []).length > 0;
  const groups = ROLES.map((r) => ({ role: r as Role, people: people.filter((p) => p.role === r) })).filter((g) => g.people.length > 0);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="page-title">People</h1>
        <div className="flex gap-2">
          {user.role === "coordinator" && (
            <>
              <Link href="/people/email?group=volunteers" className="btn-ghost px-3 py-1.5 text-xs">
                Email volunteers
              </Link>
              <Link href="/people/email?group=security" className="btn-ghost px-3 py-1.5 text-xs">
                Email security
              </Link>
            </>
          )}
          {user.role === "admin" && (
            <Link href="/people/email" className="btn-ghost px-3 py-1.5 text-xs">
              Email everyone
            </Link>
          )}
          {canCreate && (
            <Link href="/people/new" className="btn-gold px-4 py-1.5 text-xs">
              Add person
            </Link>
          )}
        </div>
      </div>

      {people.length === 0 && (
        <p className="card text-sm text-snow-faint">{canCreate ? "Nobody yet. Add the first person." : "Nobody reports to you."}</p>
      )}

      {groups.map((g) => (
        <section key={g.role}>
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-snow-faint">
            {ROLE_LABEL[g.role]}s · {g.people.length}
          </h2>
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {g.people.map((p) => (
              <Link key={p.id} href={`/people/${p.id}`} className="row border border-night-line bg-night-panel/60 hover:border-gold/40">
                <Avatar src={p.photoUrl} name={p.name ?? p.email} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{p.name ?? p.email}</span>
                  <span className="block truncate text-xs text-snow-faint">
                    {p.name ? p.email : "Invite not accepted"}
                    {p.teamName ? ` · ${p.teamName}` : ""}
                  </span>
                </span>
                <StatusBadge status={p.status} />
              </Link>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
