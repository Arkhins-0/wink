import { body, handle, str, strings } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { q } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { sendBroadcast } from "@/lib/messages";
import { isRole } from "@/lib/roles";

export const dynamic = "force-dynamic";

/** Admin: email everyone in the ticked roles, volunteers and security included. */
export const POST = handle(async (request) => {
  const admin = await requireUser(["admin"]);
  const b = await body(request);
  const roles = strings(b.roles, 20).filter(isRole);
  if (roles.length === 0) return fail("Tick at least one group.");
  const rows = await q<{ id: string }>(
    "SELECT id FROM users WHERE role = ANY($1::text[]) AND status = 'active' AND id <> $2",
    [roles, admin.id],
  );
  if (rows.length === 0) return fail("Nobody active in those groups.");
  const result = await sendBroadcast(admin, {
    recipientIds: rows.map((r) => r.id),
    body: str(b.body, 5000),
    fileId: str(b.fileId, 64) || null,
    urgent: true,
    forceEmail: true,
  });
  return json(result, 201);
});
