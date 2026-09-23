import { body, bool, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { deleteSeason, seasonArchive, seasonById, setArchived, updateSeason } from "@/lib/seasons";
import { audit } from "@/lib/users";
import { seasonInput } from "@/lib/seasonInput";

export const dynamic = "force-dynamic";

/** A season, with `?archive=1` everything of it this person was part of, read-only. */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such season.", 404);
  if (new URL(request.url).searchParams.get("archive") === "1") {
    const archive = await seasonArchive(user, id);
    if (!archive) return fail("No such season.", 404);
    return json(archive);
  }
  const season = await seasonById(id);
  if (!season) return fail("No such season.", 404);
  return json({ season });
});

/** Admin: rename or re-date, or archive / bring back (`archived: true|false`). */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such season.", 404);
  const b = await body(request);
  if (b.archived !== undefined) {
    const season = await setArchived(id, bool(b.archived));
    await audit(admin.id, id, bool(b.archived) ? "season.archived" : "season.unarchived");
    return json({ season });
  }
  const input = seasonInput(b);
  if ("error" in input) return fail(input.error);
  const season = await updateSeason(id, input);
  await audit(admin.id, id, "season.updated", input);
  return json({ season });
});

/** Admin: delete the season with its weekends, sessions and every message sent in it. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such season.", 404);
  const season = await seasonById(id);
  if (!season) return fail("No such season.", 404);
  await deleteSeason(id);
  await audit(admin.id, id, "season.deleted", { name: season.name });
  return json({ ok: true });
});
