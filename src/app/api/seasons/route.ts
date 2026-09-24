import { body, handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { seasonInput } from "@/lib/seasonInput";
import { createSeason, listSeasons } from "@/lib/seasons";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Every season, newest first; `current` marks the one everything new goes into. */
export const GET = handle(async () => {
  await requireUser();
  return json({ seasons: await listSeasons() });
});

/** Admin: a new season. It is not current until an admin makes it so. */
export const POST = handle(async (request) => {
  const admin = await requireUser(["admin"]);
  const input = seasonInput(await body(request));
  if ("error" in input) return fail(input.error);
  const season = await createSeason(input);
  await audit(admin.id, season.id, "season.created", input);
  return json({ season }, 201);
});
