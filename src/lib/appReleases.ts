import "server-only";

import { SITE_URL } from "./config";
import { env } from "./env";

/**
 * The latest GitHub release of the Android app: what GET /api/app-version
 * returns and what the app compares its own version against.
 *
 * The repository may be private. GitHub is then asked with GITHUB_TOKEN (a
 * read-only token that stays on the server), and the APK is handed out
 * through this site — /api/app-version/apk sends the phone on to a
 * short-lived download link — so neither the app nor a browser needs
 * access to GitHub.
 */

const CHECK_INTERVAL_MS = 30 * 60 * 1000;
// A user tapping "check for updates" skips the long cache, but not
// entirely: GitHub allows 60 unauthenticated calls an hour, so a burst of
// taps must still collapse into one call.
const FORCED_INTERVAL_MS = 60 * 1000;

export type ReleaseInfo = {
  version: string;
  releaseUrl: string;
  apkUrl: string | null;
  notes: string;
};

let cache: { at: number; info: ReleaseInfo | null } | null = null;
/** Where GitHub keeps the latest release's APK (the API address and the public one), for /api/app-version/apk. */
let asset: { version: string; apiUrl: string; publicUrl: string } | null = null;

const headers = (accept = "application/vnd.github+json"): Record<string, string> => ({
  Accept: accept,
  "User-Agent": "wink-server",
  ...(env.githubToken ? { Authorization: `Bearer ${env.githubToken}` } : {}),
});

/** The release body reduced to the list of changes: no headings, no link footer. */
function changesOnly(body: string): string {
  return body
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && !line.includes("Full Changelog"))
    .map((line) => line.replace(/;\s*v\d+(\.\d+)+\s*$/i, ""))
    .join("\n")
    .slice(0, 600);
}

async function fetchLatestRelease(): Promise<ReleaseInfo | null> {
  if (!env.githubRepo) return null;
  try {
    const response = await fetch(`https://api.github.com/repos/${env.githubRepo}/releases/latest`, { headers: headers(), cache: "no-store" });
    if (!response.ok) return null;
    const data = (await response.json()) as {
      tag_name?: string;
      html_url?: string;
      body?: string;
      assets?: { name: string; url: string; browser_download_url: string }[];
    };
    if (!data.tag_name || !data.html_url) return null;
    // A release carries both a signed release APK and a debug one. The debug
    // build has its own application id, so it would install beside the app
    // rather than update it — prefer the one that isn't debug.
    const apks = (data.assets ?? []).filter((a) => a.name.toLowerCase().endsWith(".apk"));
    const apk = apks.find((a) => !/debug/i.test(a.name)) ?? apks[0];
    const version = data.tag_name.replace(/^v/i, "");
    asset = apk ? { version, apiUrl: apk.url, publicUrl: apk.browser_download_url } : null;
    return {
      version,
      releaseUrl: data.html_url,
      apkUrl: apk ? `${SITE_URL}/api/app-version/apk?v=${encodeURIComponent(version)}` : null,
      notes: changesOnly(data.body ?? ""),
    };
  } catch {
    return null;
  }
}

/**
 * The latest release, checked at most once every 30 minutes across all
 * requests (an in-memory cache — fine for a single process, and kind to
 * GitHub's rate limit). A failed check falls back to whatever was cached
 * before rather than blanking the version out.
 */
export async function latestRelease(force = false): Promise<ReleaseInfo | null> {
  if (cache && Date.now() - cache.at < (force ? FORCED_INTERVAL_MS : CHECK_INTERVAL_MS)) return cache.info;
  const info = await fetchLatestRelease();
  const resolved = info ?? cache?.info ?? null;
  cache = { at: Date.now(), info: resolved };
  return resolved;
}

export type ChangelogEntry = { version: string; date: string; changes: string[] };

let listCache: { at: number; list: ChangelogEntry[] } | null = null;

/** A release body as its list of changes, without the "; v0.1.2.3" a release commit ends with. */
function changeLines(body: string): string[] {
  return body
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && !line.includes("Full Changelog"))
    .map((line) => line.replace(/^[-*]\s*/, "").replace(/;\s*v\d+(\.\d+)+\s*$/i, "").trim())
    .filter(Boolean);
}

/**
 * Every published release, newest first, for the app's "What's new" page.
 * Cached like the latest release: GitHub is asked at most every 30 minutes.
 */
export async function allReleases(): Promise<ChangelogEntry[]> {
  if (listCache && Date.now() - listCache.at < CHECK_INTERVAL_MS) return listCache.list;
  if (!env.githubRepo) return [];
  try {
    const response = await fetch(`https://api.github.com/repos/${env.githubRepo}/releases?per_page=50`, { headers: headers(), cache: "no-store" });
    if (!response.ok) return listCache?.list ?? [];
    const data = (await response.json()) as { tag_name?: string; published_at?: string; body?: string; draft?: boolean; prerelease?: boolean }[];
    const list = data
      .filter((r) => r.tag_name && !r.draft && !r.prerelease)
      .map((r) => ({ version: r.tag_name!.replace(/^v/i, ""), date: r.published_at ?? "", changes: changeLines(r.body ?? "") }));
    listCache = { at: Date.now(), list };
    return list;
  } catch {
    return listCache?.list ?? [];
  }
}

/**
 * Where to fetch the latest APK right now: GitHub's short-lived signed
 * link (asked for with the token, so it works for a private repository),
 * or the plain public link when that fails. Null when there is no APK.
 */
export async function apkDownloadUrl(): Promise<string | null> {
  if (!asset) await latestRelease();
  if (!asset) return null;
  try {
    const response = await fetch(asset.apiUrl, { headers: headers("application/octet-stream"), redirect: "manual", cache: "no-store" });
    const location = response.headers.get("location");
    if (response.status >= 300 && response.status < 400 && location) return location;
  } catch {
    // Fall through to the public link.
  }
  return asset.publicUrl;
}

/** The GitHub releases page, for when there is no release yet. */
export const releasesPage = (): string => `https://github.com/${env.githubRepo}/releases`;
