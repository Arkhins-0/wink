import { body, handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { createWeekend, listWeekends, weekendById } from "@/lib/races";
import { audit } from "@/lib/users";
import { weekendInput } from "@/lib/weekendInput";

export const dynamic = "force-dynamic";

export const GET = handle(async () => {
  await requireUser();
  return json({ weekends: await listWeekends() });
});

/** Admin: a new race weekend. Sessions are added to it afterwards. */
export const POST = handle(async (request) => {
  const admin = await requireUser(["admin"]);
  const input = weekendInput(await body(request));
  if ("error" in input) return fail(input.error);
  const id = await createWeekend(input);
  await audit(admin.id, id, "weekend.created", input);
  return json({ weekend: await weekendById(id) }, 201);
});
