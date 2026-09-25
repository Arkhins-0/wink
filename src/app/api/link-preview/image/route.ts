import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail } from "@/lib/http";
import { guardedFetch, IMAGE_BYTES, isPreviewImage } from "@/lib/linkPreview";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

/**
 * A preview's picture, fetched by the server, so a reader's phone or browser never calls the linked site. Only
 * pictures of kept previews: this is not a way to fetch anything else.
 */
export const GET = handle(async (request) => {
  await requireUser();
  const u = new URL(request.url).searchParams.get("u") ?? "";
  if (!u || !(await isPreviewImage(u))) return fail("No such picture.", 404);
  const got = await guardedFetch(u, "image/*", IMAGE_BYTES).catch(() => null);
  if (!got || !/^image\/(png|jpe?g|gif|webp|avif)/i.test(got.type)) return fail("No such picture.", 404);
  return new Response(new Uint8Array(got.body), {
    headers: {
      "content-type": got.type.split(";")[0],
      "cache-control": "private, max-age=604800, immutable",
      "x-content-type-options": "nosniff",
    },
  });
});
