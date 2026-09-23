import { fail, json } from "@/lib/http";
import { isLegalKey, LEGAL } from "@/lib/legalContent";

/**
 * The Privacy Policy or the Terms as data, for the app's own screen (which
 * keeps a copy). Public: people read them before they have an account.
 */
export async function GET(_request: Request, { params }: { params: Promise<{ doc: string }> }) {
  const { doc } = await params;
  if (!isLegalKey(doc)) return fail("No such document.", 404);
  return json(LEGAL[doc]);
}
