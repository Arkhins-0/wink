import { body, handle, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { cleanMime, MAX_FILE_BYTES, MAX_PROXY_BYTES, safeName, uploadTarget, type FileRow } from "@/lib/files";
import { fail, json } from "@/lib/http";
import { randomToken } from "@/lib/ids";

export const dynamic = "force-dynamic";

/**
 * Start an upload: the row is created and the client is told where to PUT
 * the bytes. It calls /api/files/[id]/ready when done.
 */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const name = safeName(str(b.name, 200));
  const mime = cleanMime(str(b.mime, 120).toLowerCase().split(";")[0].trim());
  const size = Number(b.size ?? 0);
  if (!name) return fail("The file needs a name.");
  if (!Number.isFinite(size) || size <= 0 || size > MAX_FILE_BYTES) return fail("Files can be up to 50 MB.");

  const key = `docs/${randomToken().slice(0, 16)}/${name}`;
  const row = await one<FileRow>(
    "INSERT INTO files (key, name, mime, size, uploaded_by) VALUES ($1, $2, $3, $4, $5) RETURNING *",
    [key, name, mime, size, user.id],
  );
  const target = await uploadTarget(row!);
  if (!target.direct && size > MAX_PROXY_BYTES) return fail("Files over 4 MB need object storage configured.", 413);
  return json({ id: row!.id, uploadUrl: target.url, direct: target.direct, maxProxyBytes: MAX_PROXY_BYTES }, 201);
});
