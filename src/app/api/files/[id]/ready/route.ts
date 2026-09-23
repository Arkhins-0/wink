import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fileById, markReady } from "@/lib/files";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** After a direct upload: confirm the bytes are in storage and open the file for use. */
export const POST = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such upload.", 404);
  const file = await fileById(id);
  if (!file || file.uploaded_by !== user.id) return fail("No such upload.", 404);
  if (!file.ready && !(await markReady(file))) return fail("The upload did not arrive. Try again.", 409);
  return json({ ok: true, id: file.id, name: file.name, mime: file.mime, size: Number(file.size) });
});
