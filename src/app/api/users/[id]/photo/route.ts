import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { canEdit } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { storePhoto } from "@/lib/profile";
import { storage } from "@/lib/storage";
import { audit, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The profile photo. Anyone signed in may see any photo: it is what the verification scan shows. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("Not found.", 404);
  const user = await userById(id);
  if (!user?.photo_key) return fail("No photo.", 404);
  const stored = await storage().get(user.photo_key);
  if (!stored) return fail("No photo.", 404);
  return new Response(new Uint8Array(stored.body), {
    headers: { "content-type": stored.contentType, "cache-control": "private, max-age=300" },
  });
});

/** Replace a photo: the person's manager or an admin, after the profile is locked. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (!canEdit(me, user)) return fail("Only this person's manager or an admin can change this.", 403);
  const form = await request.formData();
  const photo = form.get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Choose a photo.");
  const key = await storePhoto(user.id, photo);
  if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE users SET photo_key = $2 WHERE id = $1", [user.id, key]);
  await audit(me.id, user.id, "user.photo");
  return json({ ok: true, photoUrl: `/api/users/${user.id}/photo?v=${Date.now()}` });
});
