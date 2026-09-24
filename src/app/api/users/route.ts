import { body, handle, str } from "@/lib/api";
import { issueToken, requireUser } from "@/lib/auth";
import { sendInvite } from "@/lib/email";
import { canCreateRole, chatCandidates, descendants, groupCandidates } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { isRole, ROLE_LABEL } from "@/lib/roles";
import { audit, createUser, toPublic, userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Everyone below the signed-in person — or, with `?chat=1`, everyone they
 * may chat with; with `?group=1`, everyone they may bring into a group,
 * each marked `groupMode` "direct" or "request". `?role=` narrows it.
 */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const params = new URL(request.url).searchParams;
  const role = params.get("role");
  if (params.get("group") === "1") {
    const people = (await groupCandidates(user)).filter((x) => !role || x.user.role === role);
    return json({ users: people.map((x) => ({ ...toPublic(x.user), groupMode: x.mode })) });
  }
  const base = params.get("chat") === "1" ? await chatCandidates(user) : await descendants(user);
  const people = base.filter((p) => !role || p.role === role);
  return json({ users: people.map(toPublic) });
});

/** Create an account under the signed-in person and email the invite. */
export const POST = handle(async (request) => {
  const creator = await requireUser();
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  const role = b.role;
  const teamName = str(b.teamName, 80) || null;
  if (!isRole(role) || !canCreateRole(creator.role, role)) return fail("You cannot create that kind of account.", 403);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail("Enter a valid email address.");
  if (await userByEmail(email)) return fail("Someone already has that email.", 409);
  if (role === "team_manager" && !teamName) return fail("Enter the team name.");

  const user = await createUser({
    email,
    role,
    parentId: creator.id,
    createdBy: creator.id,
    // Drivers and crew inherit their manager's team.
    teamName: role === "team_manager" ? teamName : role === "driver" || role === "crew" ? creator.team_name : null,
  });
  const token = await issueToken(user.id, "invite", 24 * 7);
  await sendInvite({ email: user.email }, token, creator.name || creator.email, ROLE_LABEL[role]).catch((error) =>
    console.error("[invite]", error),
  );
  await audit(creator.id, user.id, "user.created", { role });
  return json({ user: toPublic(user) }, 201);
});
