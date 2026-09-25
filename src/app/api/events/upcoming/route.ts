import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { upcoming } from "@/lib/events";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Events this person can see that haven't ended, soonest first: Home's card and the phones' reminders. */
export const GET = handle(async () => {
  const user = await requireUser();
  return json({ events: await upcoming(user) });
});
