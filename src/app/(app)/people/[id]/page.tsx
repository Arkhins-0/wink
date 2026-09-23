import { notFound } from "next/navigation";
import { Avatar } from "@/components/Avatar";
import { PersonActions } from "@/components/PersonActions";
import { StatusBadge } from "@/components/StatusBadge";
import { canChat, canEdit, isBelow } from "@/lib/hierarchy";
import { requireProfile } from "@/lib/session";
import { toPublic, userById } from "@/lib/users";
import { q } from "@/lib/db";

export const metadata = { title: "Person" };

export default async function Person({ params }: { params: Promise<{ id: string }> }) {
  const me = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const user = await userById(id);
  if (!user || (user.id !== me.id && !(await isBelow(me, user.id)))) notFound();
  const p = toPublic(user);
  const editable = user.id !== me.id && canEdit(me, user);
  const coordinators =
    me.role === "admin" && user.role === "volunteer"
      ? await q<{ id: string; name: string | null; email: string }>("SELECT id, name, email FROM users WHERE role = 'coordinator' AND status = 'active' ORDER BY name")
      : [];

  return (
    <div className="space-y-5">
      <section className="card flex items-center gap-4">
        <Avatar src={p.photoUrl} name={p.name ?? p.email} size={72} />
        <div className="min-w-0">
          <h1 className="truncate text-lg font-semibold">{p.name ?? p.email}</h1>
          <p className="text-sm text-snow-soft">
            {p.roleLabel}
            {p.teamName ? ` · ${p.teamName}` : ""}
          </p>
          <div className="mt-1.5">
            <StatusBadge status={p.status} />
          </div>
        </div>
      </section>

      <section className="card grid gap-3 text-sm sm:grid-cols-2">
        <Row label="Email" value={p.email} />
        <Row label="Contact" value={p.phone ?? "—"} />
        <Row label="Date of birth" value={p.dob ?? "—"} />
        <Row label="Account code" value={p.verifyCode} mono />
      </section>

      <PersonActions
        person={p}
        editable={editable}
        canChat={canChat(me, user)}
        coordinators={coordinators.map((c) => ({ id: c.id, name: c.name || c.email }))}
      />
    </div>
  );
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <p className="label">{label}</p>
      <p className={mono ? "font-mono tracking-wider" : ""}>{value}</p>
    </div>
  );
}
