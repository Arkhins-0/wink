import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { nextRace } from "@/lib/races";

export const dynamic = "force-dynamic";

/** What the countdown chip shows: the live session, or the next one. */
export const GET = handle(async () => {
  await requireUser();
  return json({ ...(await nextRace()), now: new Date().toISOString() });
});
