import "server-only";

import { storage } from "./storage";

/*
 * The profile fields, validated once for both the first-login form and a
 * manager's edit. Photos are stored under photos/<user id>.<ext>.
 */

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const PHOTO_EXT: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

export type ProfileFields = { name: string; dob: string; phone: string };

export function profileFromForm(form: FormData): ProfileFields | { error: string } {
  const name = String(form.get("name") ?? "").trim().slice(0, 120);
  const dob = String(form.get("dob") ?? "").trim();
  const phone = String(form.get("phone") ?? "").trim().slice(0, 40);
  if (name.length < 2) return { error: "Enter your full name." };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dob) || Number.isNaN(Date.parse(dob))) return { error: "Enter your date of birth." };
  if (Date.parse(dob) > Date.now()) return { error: "That date of birth is in the future." };
  if (!/^\+?[\d\s()-]{6,}$/.test(phone)) return { error: "Enter a contact number." };
  return { name, dob, phone };
}

/** Save a profile photo; null when it is not an image we accept. */
export async function storePhoto(userId: string, photo: File): Promise<string | null> {
  return storeImage(`photos/${userId}`, photo);
}

/** Save an image under `<prefix>.<ext>`; null when it is not an image we accept. */
export async function storeImage(prefix: string, photo: File): Promise<string | null> {
  const ext = PHOTO_EXT[photo.type];
  if (!ext || photo.size > MAX_PHOTO_BYTES) return null;
  const key = `${prefix}.${ext}`;
  await storage().put(key, Buffer.from(await photo.arrayBuffer()), photo.type);
  return key;
}
