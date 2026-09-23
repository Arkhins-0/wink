import { json } from "@/lib/http";
import { isDatabaseConfigured, isStorageConfigured } from "@/lib/env";
import { ping } from "@/lib/db";
import { storage } from "@/lib/storage";

export const dynamic = "force-dynamic";

/**
 * Is the deployment wired up? Says which of the database and storage are
 * configured; `?deep=1` also makes one round trip to the database.
 */
export async function GET(request: Request) {
  const deep = new URL(request.url).searchParams.get("deep") === "1";
  const database = isDatabaseConfigured();
  let databaseReachable: boolean | null = null;
  if (deep && database) {
    databaseReachable = await ping().catch(() => false);
  }
  return json({
    ok: true,
    version: process.env.npm_package_version ?? null,
    database: { configured: database, reachable: databaseReachable },
    storage: { configured: isStorageConfigured(), where: storage().describe() },
  });
}
