import { json } from "@/lib/http";
import { allReleases } from "@/lib/appReleases";

export const dynamic = "force-dynamic";

/**
 * Every Android release, newest first — the app's "What's new" page.
 * Public, like /api/app-version. Shape: { releases: [{ version, date, changes[] }] }
 */
export async function GET() {
  return json({ releases: await allReleases() });
}
