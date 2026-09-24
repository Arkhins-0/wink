import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { listChannels } from "@/lib/channels";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Every race weekend's channel, season by season, the current season first. */
export const GET = handle(async () => {
  const user = await requireUser();
  return json({ seasons: await listChannels(user) });
});
