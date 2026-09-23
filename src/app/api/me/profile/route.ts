import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { profileFromForm, storePhoto } from "@/lib/profile";
import { audit, toPublic, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * First sign-in: name, date of birth, phone and a photo, once. After that
 * the profile is locked; only the person's manager or an admin changes it
 * through /api/users/[id].
 */
export const POST = handle(async (request) => {
  const user = await requireUser();
  if (user.profile_completed_at) return fail("Your profile is locked. Ask your manager or an admin to change it.", 403);
  const form = await request.formData();
  const fields = profileFromForm(form);
  if ("error" in fields) return fail(fields.error);
  const photo = form.get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Add a photo of yourself.");
  const key = await storePhoto(user.id, photo);
  if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run(
    "UPDATE users SET name = $2, dob = $3, phone = $4, photo_key = $5, profile_completed_at = now() WHERE id = $1",
    [user.id, fields.name, fields.dob, fields.phone, key],
  );
  await audit(user.id, user.id, "profile.completed");
  return json({ user: toPublic((await userById(user.id))!) });
});
