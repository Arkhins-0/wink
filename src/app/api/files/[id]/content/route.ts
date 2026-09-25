import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fileById, fileForUser, isInlineSafe, MAX_PROXY_BYTES, safeName } from "@/lib/files";
import { fail } from "@/lib/http";
import { storage } from "@/lib/storage";
import { run } from "@/lib/db";

export const dynamic = "force-dynamic";

/**
 * The bytes, through the server: the inline viewer on the site (same
 * origin, so the PDF iframe needs no extra CSP) and the local-disk
 * development fallback for uploads.
 */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such file.", 404);
  const file = await fileForUser(user.id, id);
  // Not theirs to see (or deleted for everyone): the same answer as a file that isn't there.
  if (!file || !file.ready) return fail("No such file.", 404);
  const stored = await storage().get(file.key);
  if (!stored) return fail("The file is missing from storage.", 404);
  // Only kinds safe inside a page show inline from our own site; anything else (an HTML file, say) is a download,
  // as plain bytes, so a browser can never run it here.
  const safe = isInlineSafe(file.mime);
  const inline = safe && new URL(request.url).searchParams.get("inline") === "1";
  return new Response(new Uint8Array(stored.body), {
    headers: {
      "content-type": safe ? file.mime : "application/octet-stream",
      "content-length": String(stored.body.length),
      "content-disposition": `${inline ? "inline" : "attachment"}; filename="${safeName(file.name)}"`,
      "x-content-type-options": "nosniff",
      "cache-control": "private, max-age=3600",
    },
  });
});

/** Upload through the server. Small files only; S3 direct upload is the main path. */
export const PUT = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such file.", 404);
  const file = await fileById(id);
  if (!file || file.uploaded_by !== user.id) return fail("No such upload.", 404);
  if (file.ready) return fail("Already uploaded.", 409);
  const bytes = Buffer.from(await request.arrayBuffer());
  if (bytes.length === 0 || bytes.length > MAX_PROXY_BYTES) return fail("Files over 4 MB must go straight to storage.", 413);
  await storage().put(file.key, bytes, file.mime);
  await run("UPDATE files SET ready = true, size = $2 WHERE id = $1", [file.id, bytes.length]);
  return Response.json({ ok: true });
});
