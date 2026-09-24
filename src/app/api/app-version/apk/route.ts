import { NextResponse } from "next/server";
import { apkDownloadUrl } from "@/lib/appReleases";
import { fail } from "@/lib/http";

export const dynamic = "force-dynamic";

/**
 * The latest APK — public, like /api/app-version. The phone (or a browser)
 * is sent on to a download link GitHub signs for a few minutes, so the
 * repository can stay private and the file never passes through here.
 */
export async function GET() {
  const url = await apkDownloadUrl();
  if (!url) return fail("No release has been published yet.", 404);
  return NextResponse.redirect(url, 302);
}
