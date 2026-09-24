// Applies db/migrations/*.sql in name order, once each, recording them in
// schema_migrations. Reads DATABASE_URL (or DATABASE_URL_POOLED) from the
// environment, then .env.local (a test branch, when there is one), then
// the root .env — the first to set it wins, as with Next.js. It says which
// database host it is about to change. Usage: npm run migrate
import fs from "node:fs";
import path from "node:path";
import pg from "pg";

for (const file of [".env.local", ".env"]) {
  try {
    process.loadEnvFile(path.resolve(file));
  } catch {
    // Not there: the next file, or the host's environment, carries it.
  }
}

const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (url) console.log(`database: ${new URL(url).hostname}`);
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}

const dir = path.resolve("db/migrations");
const files = fs
  .readdirSync(dir)
  .filter((f) => f.endsWith(".sql"))
  .sort();

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  await client.query(
    "CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())",
  );
  const { rows } = await client.query("SELECT name FROM schema_migrations");
  const done = new Set(rows.map((r) => r.name));
  for (const file of files) {
    if (done.has(file)) continue;
    const sql = fs.readFileSync(path.join(dir, file), "utf8");
    process.stdout.write(`applying ${file}... `);
    await client.query("BEGIN");
    try {
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations (name) VALUES ($1)", [file]);
      await client.query("COMMIT");
      console.log("ok");
    } catch (error) {
      await client.query("ROLLBACK");
      console.log("failed");
      throw error;
    }
  }
  console.log("up to date");
} finally {
  await client.end();
}
