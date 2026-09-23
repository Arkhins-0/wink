import "server-only";

import { after } from "next/server";
import { q, run } from "./db";
import { sendNotice } from "./email";
import { pushSync, pushTo, type Push, type SyncSignal } from "./push";
import { NO_AUTO_EMAIL, type Role } from "./roles";
import { SITE_URL } from "./config";

/*
 * Delivery, in one place. A message is written for its recipients, then
 * — after the response has gone out — pushed to their devices and, when
 * it is urgent, emailed to those who get automatic email. Volunteers and
 * security never get automatic email; their coordinator forwards it with
 * `forceEmail`.
 */

export type Delivery = {
  messageId: string;
  recipientIds: string[];
  push: Push;
  /** The silent nudge that makes the recipients' phones fetch it at once (and so mark it delivered). */
  sync?: SyncSignal;
  email?: {
    subject: string;
    title: string;
    body: string;
    /** Send to volunteers and security too (a coordinator relay, or the admin's bulk mail). */
    force?: boolean;
  };
};

export async function deliver(d: Delivery): Promise<void> {
  const ids = Array.from(new Set(d.recipientIds));
  if (ids.length === 0) return;

  // One INSERT for the whole fan-out.
  await run(
    `INSERT INTO message_recipients (message_id, user_id)
     SELECT $1, unnest($2::uuid[]) ON CONFLICT DO NOTHING`,
    [d.messageId, ids],
  );

  after(async () => {
    if (d.sync) await pushSync(ids, d.sync);
    await pushTo(ids, d.push).catch((error) => console.error("[notify] push", error));
    if (!d.email) return;
    const roles = d.email.force ? null : NO_AUTO_EMAIL;
    const people = await q<{ email: string; name: string | null; role: Role }>(
      `SELECT email, name, role FROM users
       WHERE id = ANY($1::uuid[]) AND status = 'active'
         AND ($2::text[] IS NULL OR NOT (role = ANY($2::text[])))`,
      [ids, roles],
    );
    await sendNotice(people, d.email.subject, d.email.title, d.email.body, `${SITE_URL}${d.push.link}`).catch(
      (error) => console.error("[notify] email", error),
    );
  });
}

/** Everyone who can currently use the app. */
export async function activeUserIds(except?: string): Promise<string[]> {
  const rows = await q<{ id: string }>("SELECT id FROM users WHERE status = 'active' AND id <> COALESCE($1, '00000000-0000-0000-0000-000000000000'::uuid)", [
    except ?? null,
  ]);
  return rows.map((r) => r.id);
}

/** A short preview for a push body. */
export function preview(body: string, fileName?: string | null): string {
  const text = body.trim().replace(/\s+/g, " ");
  if (text) return text.length > 140 ? `${text.slice(0, 137)}…` : text;
  return fileName ? `Document: ${fileName}` : "New message";
}
