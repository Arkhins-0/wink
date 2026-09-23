import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { unreadCount } from "@/lib/messages";
import { isPushConfigured } from "@/lib/push";
import { CREATE_RULES, CHANNEL_POSTERS, ROLE_LABEL } from "@/lib/roles";
import { qrUrl, toPublic, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The signed-in person, and what they may do. */
export const GET = handle(async () => {
  const user = await requireUser();
  const parent = user.parent_id ? await userById(user.parent_id) : undefined;
  return json({
    user: toPublic(user),
    qrUrl: qrUrl(user),
    parent: parent ? { id: parent.id, name: parent.name || parent.email, roleLabel: ROLE_LABEL[parent.role] } : null,
    canCreate: CREATE_RULES[user.role] ?? [],
    canPostChannel: CHANNEL_POSTERS.includes(user.role),
    canRelay: user.role === "coordinator",
    canBulkEmail: user.role === "admin",
    isAdmin: user.role === "admin",
    unread: await unreadCount(user.id),
    pushConfigured: isPushConfigured(),
  });
});
