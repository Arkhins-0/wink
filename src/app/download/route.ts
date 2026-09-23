import { NextResponse } from "next/server";
import { latestRelease, releasesPage } from "@/lib/appReleases";
import { appPath } from "@/lib/appLink";
import { SITE_URL } from "@/lib/config";

export const dynamic = "force-dynamic";

const PACKAGE = "com.arkhins.wink";

/**
 * wink.arkhins.com/download — the one link to share.
 *
 * On an Android phone it first tries to open the installed app (an intent
 * link to /home, or to `?open=<path>` when the app has that page); only
 * when the app is not there does the browser fall back to downloading the
 * APK, through `?direct=1`.
 * Everywhere else it goes straight to the latest release APK, or to the
 * releases page when there is none yet.
 */
export async function GET(request: Request) {
  const url = new URL(request.url);
  const android = /android/i.test(request.headers.get("user-agent") ?? "");
  if (android && url.searchParams.get("direct") !== "1") {
    const host = new URL(SITE_URL).host;
    const fallback = encodeURIComponent(`${SITE_URL}/download?direct=1`);
    const target = appPath(url.searchParams.get("open"));
    return NextResponse.redirect(`intent://${host}${target}#Intent;scheme=https;package=${PACKAGE};S.browser_fallback_url=${fallback};end`, 302);
  }
  const info = await latestRelease();
  return NextResponse.redirect(info?.apkUrl ?? info?.releaseUrl ?? releasesPage(), 302);
}
