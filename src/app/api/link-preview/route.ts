import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { previewFor, previewOut } from "@/lib/linkPreview";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

/** The card for a link being typed: `?url=`. `{ preview: null }` when the page gives nothing to show. */
export const GET = handle(async (request) => {
  await requireUser();
  const url = new URL(request.url).searchParams.get("url") ?? "";
  if (url.length > 2000) return json({ preview: null });
  const p = await previewFor(url).catch(() => null);
  return json({ preview: previewOut(p) });
});
