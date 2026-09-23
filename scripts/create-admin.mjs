// The first admin. Creates a pending admin account for the email given and
// prints the invite link (it is also emailed when Brevo is configured).
// Usage: npm run create-admin -- someone@example.com
import path from "node:path";
import { createHash, randomBytes, randomInt } from "node:crypto";
import pg from "pg";

try {
  process.loadEnvFile(path.resolve(".env"));
} catch {
  // The host's environment must carry the keys then.
}

const email = (process.argv[2] ?? "").trim().toLowerCase();
if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
  console.error("Usage: npm run create-admin -- someone@example.com");
  process.exit(1);
}
const url = process.env.DATABASE_URL || process.env.DATABASE_URL_POOLED;
if (!url) {
  console.error("DATABASE_URL is not set.");
  process.exit(1);
}
const site = (process.env.NEXT_PUBLIC_SITE_URL || "https://wink.arkhins.com").replace(/\/+$/, "");

const ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ2345679";
const code = () => {
  let s = "";
  for (let i = 0; i < 8; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return `${s.slice(0, 4)}-${s.slice(4)}`;
};
const token = () => randomBytes(32).toString("base64url");
const sha = (t) => createHash("sha256").update(t).digest("hex");

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: true } });
await client.connect();
try {
  let { rows } = await client.query("SELECT id, status FROM users WHERE email = $1", [email]);
  let user = rows[0];
  if (!user) {
    ({ rows } = await client.query(
      "INSERT INTO users (email, role, verify_code, qr_token) VALUES ($1, 'admin', $2, $3) RETURNING id, status",
      [email, code(), token()],
    ));
    user = rows[0];
    console.log(`created admin ${email}`);
  } else if (user.status !== "pending") {
    console.log(`${email} already exists and is ${user.status}; nothing to do`);
    process.exit(0);
  } else {
    console.log(`${email} exists and is pending; issuing a fresh invite`);
  }

  const invite = token();
  await client.query("UPDATE auth_tokens SET used_at = now() WHERE user_id = $1 AND kind = 'invite' AND used_at IS NULL", [user.id]);
  await client.query("INSERT INTO auth_tokens (hash, user_id, kind, expires_at) VALUES ($1, $2, 'invite', now() + interval '7 days')", [
    sha(invite),
    user.id,
  ]);
  const link = `${site}/invite/${invite}`;
  console.log(`invite link (7 days): ${link}`);

  if (process.env.BREVO_API_KEY && process.env.EMAIL_FROM) {
    const response = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: { "api-key": process.env.BREVO_API_KEY, "content-type": "application/json" },
      body: JSON.stringify({
        sender: { email: process.env.EMAIL_FROM, name: process.env.EMAIL_FROM_NAME || "Wink" },
        to: [{ email }],
        subject: "Your Wink admin account",
        htmlContent: `<p>Your Wink admin account is ready. Open the link to choose a password:</p><p><a href="${link}">${link}</a></p><p>It works for 7 days.</p>`,
      }),
    });
    console.log(response.ok ? "invite emailed" : `email failed: ${response.status} ${await response.text()}`);
  }
} finally {
  await client.end();
}
