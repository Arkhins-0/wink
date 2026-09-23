import { body, handle, str } from "@/lib/api";
import { issueToken, recordAttempt, tooManyAttempts } from "@/lib/auth";
import { sendReset } from "@/lib/email";
import { clientAddress, json } from "@/lib/http";
import { userByEmail } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Always answers the same, so the form cannot be used to find out who has an account. */
export const POST = handle(async (request) => {
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  const ip = clientAddress(request);
  if (!email || (await tooManyAttempts(email, ip))) return json({ ok: true });
  await recordAttempt(email, ip);
  const user = await userByEmail(email);
  if (user && (user.status === "active" || user.status === "suspended")) {
    const token = await issueToken(user.id, "reset", 2);
    await sendReset({ email: user.email, name: user.name }, token).catch((error) => console.error("[forgot]", error));
  }
  return json({ ok: true });
});
