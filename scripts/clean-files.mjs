// Uploaded files nothing points to any more: every message that carried them
// was deleted (or its season was), and no forward still does. Like WhatsApp,
// a file is kept while anything references it; once nothing has for DAYS
// days, it can go. Profile and group photos are not in `files` and are
// never touched.
//
// Reports only, unless told to delete:
//   npm run clean-files              # how many, how big, oldest
//   npm run clean-files -- --delete  # remove them: the row, then the bytes in S3
//
// Reads DATABASE_URL and the S3 settings like scripts/migrate.mjs does
// (.env.local first, then .env) and says which database it is looking at.
import path from "node:path";
import pg from "pg";
import { DeleteObjectCommand, S3Client } from "@aws-sdk/client-s3";

for (const file of [".env.local", ".env"]) {
  try {
    process.loadEnvFile(path.resolve(file));
  } catch {
    // Not there: the next file, or the host's environment, carries it.
  }
}

const DAYS = 30;
const remove = process.argv.includes("--delete");

const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}
console.log(`database: ${new URL(url).hostname}`);

// Unreferenced by any message (live or deleted placeholder), and old enough that no upload still on its way (a
// phone's outbox posts right after uploading) could be about to use it.
const UNUSED = `
  FROM files f
  WHERE f.created_at < now() - make_interval(days => ${DAYS})
    AND NOT EXISTS (SELECT 1 FROM message_files mf WHERE mf.file_id = f.id)
    AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.file_id = f.id)`;

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  const { rows } = await client.query(`SELECT f.id, f.key, f.size, f.created_at ${UNUSED} ORDER BY f.created_at`);
  const bytes = rows.reduce((sum, r) => sum + Number(r.size || 0), 0);
  console.log(`unused for ${DAYS}+ days: ${rows.length} files, ${(bytes / 1024 / 1024).toFixed(1)} MB`);
  if (rows.length) console.log(`oldest: ${new Date(rows[0].created_at).toISOString().slice(0, 10)}`);
  if (!remove) {
    console.log("Nothing deleted. Run with --delete to remove them.");
  } else if (rows.length) {
    const bucket = process.env.S3_BUCKET;
    const prefix = (process.env.S3_PREFIX ?? "wink").replace(/^\/+|\/+$/g, "");
    if (!bucket) throw new Error("S3_BUCKET is not set.");
    const s3 = new S3Client({
      region: process.env.AWS_REGION || "us-east-2",
      ...(process.env.AWS_ENDPOINT_URL_S3 ? { endpoint: process.env.AWS_ENDPOINT_URL_S3, forcePathStyle: true } : {}),
      credentials: { accessKeyId: process.env.AWS_ACCESS_KEY_ID, secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY },
    });
    let done = 0;
    for (const row of rows) {
      // Checked again as it goes: a forward made since the list was read keeps its file.
      const gone = await client.query(`DELETE FROM files WHERE id = $1 AND id IN (SELECT f.id ${UNUSED}) RETURNING key`, [row.id]);
      if (!gone.rowCount) continue;
      try {
        await s3.send(new DeleteObjectCommand({ Bucket: bucket, Key: prefix ? `${prefix}/${row.key}` : row.key }));
      } catch (error) {
        console.warn(`row ${row.id} removed, but its bytes stayed in storage (${row.key}): ${error.message}`);
      }
      done++;
    }
    console.log(`deleted ${done} files.`);
  }
} finally {
  await client.end();
}
