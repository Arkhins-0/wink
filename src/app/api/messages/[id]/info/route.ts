import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one, q } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { ROLE_LABEL, type Role } from "@/lib/roles";

export const dynamic = "force-dynamic";

/**
 * Who a message you sent in a private chat or a group has reached: for
 * each person, when it was delivered to their phone and when they read it.
 * Only the sender may ask.
 */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such message.", 404);
  const m = await one<{ sender_id: string | null; kind: string | null; created_at: string }>(
    `SELECT m.sender_id, c.kind, m.created_at FROM messages m LEFT JOIN conversations c ON c.id = m.conversation_id WHERE m.id = $1`,
    [id],
  );
  if (!m || (m.kind !== "direct" && m.kind !== "group")) return fail("No such message.", 404);
  if (m.sender_id !== user.id) return fail("Only the sender can see who it reached.", 403);
  const rows = await q<{ id: string; name: string | null; email: string; role: Role; photo_key: string | null; delivered_at: string | null; read_at: string | null }>(
    `SELECT u.id, u.name, u.email, u.role, u.photo_key, r.delivered_at, r.read_at
     FROM message_recipients r JOIN users u ON u.id = r.user_id
     WHERE r.message_id = $1 ORDER BY r.read_at DESC NULLS LAST, r.delivered_at DESC NULLS LAST, u.name`,
    [id],
  );
  const iso = (v: string | null) => (v ? new Date(v).toISOString() : null);
  return json({
    sentAt: new Date(m.created_at).toISOString(),
    recipients: rows.map((r) => ({
      id: r.id,
      name: r.name || r.email,
      roleLabel: ROLE_LABEL[r.role],
      photoUrl: r.photo_key ? `/api/users/${r.id}/photo` : null,
      // Reading implies delivery, even if the delivery mark never landed.
      deliveredAt: iso(r.delivered_at ?? r.read_at),
      readAt: iso(r.read_at),
    })),
  });
});
