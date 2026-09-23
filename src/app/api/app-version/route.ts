import { fail, json } from "@/lib/http";
import { latestRelease } from "@/lib/appReleases";

export const dynamic = "force-dynamic";

/**
 * The latest Android release — public, no session needed. This is what the
 * app's UpdateChecker calls. `?fresh=1` is a user asking in as many words,
 * from the app's version card, and looks past the half-hour cache.
 *
 * Shape: { version, releaseUrl, apkUrl | null, notes }
 * 404 when no release has been published yet (the app treats it as "none",
 * not as a failure).
 */
export async function GET(request: Request) {
  const fresh = new URL(request.url).searchParams.get("fresh") === "1";
  const info = await latestRelease(fresh);
  if (!info) return fail("No release has been published yet.", 404);
  return json(info);
}
