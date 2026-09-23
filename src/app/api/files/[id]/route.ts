import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { downloadUrl, fileById, isOfficeMime } from "@/lib/files";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/**
 * A document: its details and where to fetch it. `?go=download` or
 * `?go=view` answers with a redirect straight to the bytes instead, which
 * is what a link in the page and the app's downloader use.
 */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such file.", 404);
  const file = await fileById(id);
  if (!file || !file.ready) return fail("No such file.", 404);

  const go = new URL(request.url).searchParams.get("go");
  if (go === "download" || go === "view") {
    const url = await downloadUrl(file, go === "view");
    return Response.redirect(new URL(url, request.url), 302);
  }

  return json({
    id: file.id,
    name: file.name,
    mime: file.mime,
    size: Number(file.size),
    downloadUrl: `/api/files/${file.id}?go=download`,
    viewUrl: `/api/files/${file.id}/content?inline=1`,
    // Office files cannot render in a browser by themselves: Microsoft's
    // viewer takes a temporary signed link to the bytes.
    officeViewerUrl: isOfficeMime(file.mime)
      ? `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(await downloadUrl(file, true))}`
      : null,
  });
});
