import "server-only";

import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { one, run } from "./db";

/*
 * Link previews, the way WhatsApp does them: the first link in a message gets a card with the page's title, its
 * description, its picture and its site. The server fetches the page (never the viewers' phones or browsers), keeps
 * what it found for a week, and shows the picture through itself.
 *
 * Fetching someone else's URL from the server is guarded: http(s) only, ports 80 and 443, never an address inside a
 * private network (checked for every redirect), six seconds, and a page read only up to its </head> (2 MB at most).
 */

export type LinkPreview = { url: string; title: string; description: string; image: string | null; site: string };

const KEEP_MS = 7 * 24 * 60 * 60 * 1000;
/** A page that gave nothing is asked again sooner. */
const KEEP_EMPTY_MS = 60 * 60 * 1000;
// The tags are in the <head>, which some pages (YouTube) put after 700 KB of scripts; reading stops at </head>.
const PAGE_BYTES = 2 * 1024 * 1024;
export const IMAGE_BYTES = 5 * 1024 * 1024;
const TIMEOUT_MS = 6000;

/** The first http(s) link in a text, trimmed of the punctuation a sentence puts after it. */
export function firstUrl(text: string): string | null {
  const m = /\bhttps?:\/\/[^\s<>"']+/i.exec(text);
  if (!m) return null;
  return m[0].replace(/[.,;:!?)\]}'"]+$/, "");
}

/** The link as the key it is kept under: a URL the browser would treat the same, without its #fragment. */
export function normalUrl(raw: string): string | null {
  try {
    const u = new URL(raw.trim());
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    u.hash = "";
    return u.toString();
  } catch {
    return null;
  }
}

function privateAddress(ip: string): boolean {
  if (isIP(ip) === 4) {
    const [a, b] = ip.split(".").map(Number);
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127) || a >= 224;
  }
  const v6 = ip.toLowerCase();
  if (v6.startsWith("::ffff:")) return privateAddress(v6.slice(7));
  return v6 === "::1" || v6 === "::" || v6.startsWith("fc") || v6.startsWith("fd") || v6.startsWith("fe8") || v6.startsWith("fe9") ||
    v6.startsWith("fea") || v6.startsWith("feb") || v6.startsWith("ff");
}

/** May the server fetch this URL: public http(s) on the usual ports, every address it resolves to public. */
async function allowed(u: URL): Promise<boolean> {
  if (u.protocol !== "http:" && u.protocol !== "https:") return false;
  if (u.port && u.port !== "80" && u.port !== "443") return false;
  if (u.username || u.password) return false;
  const host = u.hostname.replace(/^\[|\]$/g, "");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".internal") || host.endsWith(".local")) return false;
  if (isIP(host)) return !privateAddress(host);
  try {
    const addresses = await lookup(host, { all: true });
    return addresses.length > 0 && addresses.every((a) => !privateAddress(a.address));
  } catch {
    return false;
  }
}

/** GET with the guards above, redirects followed by hand (each hop checked), the body cut at [max] bytes. */
export async function guardedFetch(start: string, accept: string, max: number, stopAt?: string): Promise<{ url: string; type: string; body: Buffer } | null> {
  let url = start;
  for (let hop = 0; hop < 5; hop++) {
    const u = new URL(url);
    if (!(await allowed(u))) return null;
    const res = await fetch(u, {
      redirect: "manual",
      signal: AbortSignal.timeout(TIMEOUT_MS),
      // Many sites only give a card to what looks like a link-preview bot.
      headers: { accept, "user-agent": "Mozilla/5.0 (compatible; WinkLinkPreview/1.0; +https://wink.arkhins.com) facebookexternalhit/1.1" },
    }).catch(() => null);
    if (!res) return null;
    if (res.status >= 300 && res.status < 400) {
      const next = res.headers.get("location");
      if (!next) return null;
      url = new URL(next, u).toString();
      continue;
    }
    if (!res.ok || !res.body) return null;
    const chunks: Buffer[] = [];
    let size = 0;
    const reader = res.body.getReader();
    let tail = "";
    while (size < max) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(Buffer.from(value));
      size += value.byteLength;
      if (stopAt) {
        // The last chunk and a little before it, so a tag split across two chunks is still seen.
        const seen = tail + Buffer.from(value).toString("latin1");
        if (seen.toLowerCase().includes(stopAt)) break;
        tail = seen.slice(-stopAt.length);
      }
    }
    await reader.cancel().catch(() => {});
    return { url, type: res.headers.get("content-type") ?? "", body: Buffer.concat(chunks).subarray(0, max) };
  }
  return null;
}

const ENTITIES: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };
const decode = (s: string) =>
  s
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&([a-z]+);/gi, (m, n) => ENTITIES[n.toLowerCase()] ?? m)
    .replace(/\s+/g, " ")
    .trim();

/** What a page says about itself: Open Graph first, then Twitter's card, then the plain title and description. */
export function readPage(html: string, pageUrl: string): Omit<LinkPreview, "url"> {
  const head = html.slice(0, PAGE_BYTES);
  const metas = new Map<string, string>();
  for (const tag of head.match(/<meta\b[^>]*>/gi) ?? []) {
    const attr = (name: string) => new RegExp(`\\b${name}\\s*=\\s*("([^"]*)"|'([^']*)'|([^\\s>]+))`, "i").exec(tag);
    const key = attr("property") ?? attr("name");
    const content = attr("content");
    if (!key || !content) continue;
    const k = (key[2] ?? key[3] ?? key[4] ?? "").toLowerCase();
    if (!metas.has(k)) metas.set(k, decode(content[2] ?? content[3] ?? content[4] ?? ""));
  }
  const pick = (...keys: string[]) => keys.map((k) => metas.get(k)).find((v) => v && v.length > 0) ?? "";
  const titleTag = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(head)?.[1];
  const image = pick("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src");
  let imageUrl: string | null = null;
  try {
    imageUrl = image ? new URL(image, pageUrl).toString() : null;
    if (imageUrl && !/^https?:/i.test(imageUrl)) imageUrl = null;
  } catch {
    imageUrl = null;
  }
  return {
    title: (pick("og:title", "twitter:title") || decode(titleTag ?? "")).slice(0, 300),
    description: pick("og:description", "twitter:description", "description").slice(0, 500),
    image: imageUrl,
    site: (pick("og:site_name") || new URL(pageUrl).hostname.replace(/^www\./, "")).slice(0, 100),
  };
}

type Row = { url: string; title: string; description: string; image_url: string | null; site_name: string; fetched_at: string };
const out = (r: Row): LinkPreview => ({ url: r.url, title: r.title, description: r.description, image: r.image_url, site: r.site_name });

/** The preview of a link: kept for a week, else fetched now. Null when the page gives nothing worth a card. */
export async function previewFor(raw: string): Promise<LinkPreview | null> {
  const url = normalUrl(raw);
  if (!url) return null;
  const kept = await one<Row>("SELECT * FROM link_previews WHERE url = $1", [url]);
  const has = (r: Row) => Boolean(r.title || r.description);
  if (kept && Date.now() - new Date(kept.fetched_at).getTime() < (has(kept) ? KEEP_MS : KEEP_EMPTY_MS)) return has(kept) ? out(kept) : null;

  const page = await guardedFetch(url, "text/html,application/xhtml+xml", PAGE_BYTES, "</head>").catch(() => null);
  const found = page && /html/i.test(page.type) ? readPage(page.body.toString("utf8"), page.url) : null;
  // A page with nothing to say is kept too (as empty), so it is not asked again every keystroke.
  const row = await one<Row>(
    `INSERT INTO link_previews (url, title, description, image_url, site_name, fetched_at) VALUES ($1, $2, $3, $4, $5, now())
     ON CONFLICT (url) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description, image_url = EXCLUDED.image_url,
       site_name = EXCLUDED.site_name, fetched_at = now()
     RETURNING *`,
    [url, found?.title ?? "", found?.description ?? "", found?.image ?? null, found?.site ?? ""],
  );
  return row && (row.title || row.description) ? out(row) : null;
}

/**
 * The link a message keeps a preview of: the one the sender left open, if it is in the text and the server has a
 * card for it. Anything else (closed, edited away, never fetched) is no preview.
 */
export async function linkForMessage(body: string, linkUrl: string | null | undefined): Promise<string | null> {
  if (!linkUrl) return null;
  const url = normalUrl(linkUrl);
  const inText = firstUrl(body);
  if (!url || !inText || normalUrl(inText) !== url) return null;
  const kept = await one<{ url: string }>("SELECT url FROM link_previews WHERE url = $1 AND (title <> '' OR description <> '')", [url]);
  return kept?.url ?? null;
}

/** Whether a picture belongs to a kept preview: the image route shows nothing else. */
export async function isPreviewImage(imageUrl: string): Promise<boolean> {
  return Boolean(await one("SELECT 1 FROM link_previews WHERE image_url = $1 LIMIT 1", [imageUrl]));
}

/** After an edit: the preview stays only while its link is still the first in the text. */
export async function keepLinkAfterEdit(messageId: string, body: string): Promise<void> {
  const inText = firstUrl(body);
  await run("UPDATE messages SET link_url = NULL WHERE id = $1 AND link_url IS NOT NULL AND link_url IS DISTINCT FROM $2", [
    messageId,
    inText ? normalUrl(inText) : null,
  ]);
}

/** A message's preview as the apps show it: the picture through this server. */
export function previewOut(row: { url: string; title: string; description: string; image: string | null; site: string } | null) {
  if (!row || (!row.title && !row.description)) return null;
  return {
    url: row.url,
    title: row.title,
    description: row.description,
    site: row.site,
    image: row.image ? `/api/link-preview/image?u=${encodeURIComponent(row.image)}` : null,
  };
}
