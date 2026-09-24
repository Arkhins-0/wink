import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { groupPhoto, setGroupPhoto } from "@/lib/groups";
import { fail, json } from "@/lib/http";
import { storage } from "@/lib/storage";

export const dynamic = "force-dynamic";

/** The group's picture. Anyone signed in may see it: it sits in the chat list. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("Not found.", 404);
  const key = await groupPhoto(id);
  if (!key) return fail("No photo.", 404);
  const stored = await storage().get(key);
  if (!stored) return fail("No photo.", 404);
  return new Response(new Uint8Array(stored.body), {
    headers: { "content-type": stored.contentType, "cache-control": "private, max-age=300" },
  });
});

/** Admin: a new picture (multipart, field `photo`). */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such group.", 404);
  const form = await request.formData();
  const photo = form.get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Choose a photo.");
  await setGroupPhoto(user, id, photo);
  return json({ ok: true, photoUrl: `/api/groups/${id}/photo?v=${Date.now()}` });
});
