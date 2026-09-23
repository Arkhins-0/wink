import "server-only";

import { redirect } from "next/navigation";
import { currentUser, type SessionUser } from "./auth";

/** Server components: the signed-in person, or off to the sign-in page. */
export async function requireSession(): Promise<SessionUser> {
  const user = await currentUser();
  if (!user) redirect("/");
  return user;
}

/** Signed in and set up; sends anyone with an unfinished profile to onboarding. */
export async function requireProfile(): Promise<SessionUser> {
  const user = await requireSession();
  if (!user.profile_completed_at) redirect("/onboarding");
  return user;
}
