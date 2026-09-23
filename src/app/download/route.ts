import { NextResponse } from "next/server";
import { latestRelease, releasesPage } from "@/lib/appReleases";

export const dynamic = "force-dynamic";

/**
 * wink.arkhins.com/download — the one link to share. Sends the visitor to
 * the latest release APK, or to the releases page when there is none yet.
 */
export async function GET() {
  const info = await latestRelease();
  return NextResponse.redirect(info?.apkUrl ?? info?.releaseUrl ?? releasesPage(), 302);
}
