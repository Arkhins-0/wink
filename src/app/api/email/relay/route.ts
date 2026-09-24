import { body, handle, str, uuids } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { q } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { sendBroadcast } from "@/lib/messages";

export const dynamic = "force-dynamic";

/**
 * A coordinator forwards something by email to their own volunteers, or
 * to the security under them — the two groups that never get automatic
 * mail. It lands in their inbox too.
 */
export const POST = handle(async (request) => {
  const me = await requireUser(["coordinator", "admin"]);
  const b = await body(request);
  const group = b.group === "volunteers" ? "volunteers" : b.group === "security" ? "security" : null;
  if (!group) return fail("Choose volunteers or security.");

  const rows =
    group === "volunteers"
      ? await q<{ id: string }>(
          me.role === "admin"
            ? "SELECT id FROM users WHERE role = 'volunteer' AND status = 'active'"
            : "SELECT id FROM users WHERE role = 'volunteer' AND parent_id = $1 AND status = 'active'",
          me.role === "admin" ? [] : [me.id],
        )
      : await q<{ id: string }>(
          me.role === "admin"
            ? "SELECT id FROM users WHERE role IN ('security', 'security_head') AND status = 'active'"
            : `WITH RECURSIVE below AS (
                 SELECT id FROM users WHERE parent_id = $1
                 UNION SELECT u.id FROM users u JOIN below b ON u.parent_id = b.id)
               SELECT u.id FROM users u JOIN below b ON b.id = u.id
               WHERE u.role IN ('security', 'security_head') AND u.status = 'active'`,
          me.role === "admin" ? [] : [me.id],
        );
  if (rows.length === 0) return fail(`You have no active ${group} to email.`);

  const result = await sendBroadcast(me, {
    recipientIds: rows.map((r) => r.id),
    body: str(b.body, 5000),
    fileId: str(b.fileId, 64) || null,
    fileIds: uuids(b.fileIds),
    urgent: true,
    forceEmail: true,
  });
  return json(result, 201);
});
