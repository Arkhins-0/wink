import { json } from "@/lib/http";
import { webFirebaseConfig } from "@/lib/firebaseWeb";

export const dynamic = "force-dynamic";

/** Public, non-secret settings the browser needs: the Firebase web app, if one is set up. */
export function GET() {
  return json({ firebase: webFirebaseConfig() });
}
