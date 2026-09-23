import "server-only";

import fs from "node:fs";
import path from "node:path";
import { cert, getApps, initializeApp, type App } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { env } from "./env";
import { q, run } from "./db";

/*
 * Firebase Cloud Messaging: one message to every device and browser a set
 * of people have registered. Tokens Firebase reports as dead are removed.
 */

declare global {
  // eslint-disable-next-line no-var
  var __winkFirebase: App | null | undefined;
}

function serviceAccount(): Record<string, string> | null {
  const raw = env.firebase.serviceAccount;
  if (raw) {
    const text = raw.trim().startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
    return JSON.parse(text);
  }
  if (env.firebase.serviceAccountFile) {
    const file = path.resolve(process.cwd(), env.firebase.serviceAccountFile);
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, "utf8"));
  }
  return null;
}

function app(): App | null {
  if (globalThis.__winkFirebase !== undefined) return globalThis.__winkFirebase;
  const existing = getApps()[0];
  if (existing) return (globalThis.__winkFirebase = existing);
  const account = serviceAccount();
  globalThis.__winkFirebase = account ? initializeApp({ credential: cert(account) }) : null;
  if (!account) console.warn("[push] FIREBASE_SERVICE_ACCOUNT is not set; push is off");
  return globalThis.__winkFirebase;
}

export const isPushConfigured = (): boolean => app() !== null;

export type Push = {
  title: string;
  body: string;
  /** Where the app should go when tapped: a route like `/chats/<id>` or `/w/<id>`. */
  link: string;
  /** A stable tag so repeated posts in the same place collapse. */
  tag?: string;
};

/** Send one notification to everyone in `userIds`. Fire and forget; never throws. */
export async function pushTo(userIds: string[], push: Push): Promise<void> {
  const firebase = app();
  if (!firebase || userIds.length === 0) return;
  const rows = await q<{ token: string }>("SELECT token FROM push_tokens WHERE user_id = ANY($1::uuid[])", [userIds]);
  const tokens = rows.map((r) => r.token);
  if (tokens.length === 0) return;

  const messaging = getMessaging(firebase);
  const dead: string[] = [];
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    try {
      const result = await messaging.sendEachForMulticast({
        tokens: batch,
        notification: { title: push.title, body: push.body },
        data: { link: push.link, title: push.title, body: push.body },
        android: {
          priority: "high",
          notification: { channelId: "wink_alerts", tag: push.tag, clickAction: "OPEN_LINK", sound: "default" },
        },
        webpush: {
          notification: { title: push.title, body: push.body, tag: push.tag, icon: "/icon-512.png" },
          fcmOptions: { link: push.link },
        },
      });
      result.responses.forEach((r, idx) => {
        const code = r.error?.code ?? "";
        if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) dead.push(batch[idx]);
      });
    } catch (error) {
      console.error("[push] send failed", error);
    }
  }
  if (dead.length > 0) await run("DELETE FROM push_tokens WHERE token = ANY($1::text[])", [dead]).catch(() => null);
}
