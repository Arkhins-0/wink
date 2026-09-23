import "server-only";

import { env, isEmailConfigured } from "./env";
import { APP_NAME, SITE_URL } from "./config";

/*
 * Transactional email through Brevo's HTTP API. One call can carry many
 * recipients as separate "message versions", so a race-wide notice is one
 * request, not hundreds — and nobody sees anyone else's address.
 */

export type Recipient = { email: string; name?: string | null };

const BREVO_URL = "https://api.brevo.com/v3/smtp/email";
const VERSIONS_PER_CALL = 500;

export async function sendEmail(to: Recipient[], subject: string, html: string, text?: string): Promise<void> {
  const recipients = to.filter((r) => r.email);
  if (recipients.length === 0) return;
  if (!isEmailConfigured()) {
    console.warn(`[email] not configured; would send "${subject}" to ${recipients.length} recipient(s)`);
    return;
  }
  if (env.email.provider !== "brevo") throw new Error(`Unsupported EMAIL_PROVIDER: ${env.email.provider}`);

  for (let i = 0; i < recipients.length; i += VERSIONS_PER_CALL) {
    const chunk = recipients.slice(i, i + VERSIONS_PER_CALL);
    const body = {
      sender: { email: env.email.from, name: env.email.fromName },
      subject,
      htmlContent: html,
      textContent: text ?? stripHtml(html),
      // Brevo needs a top-level `to` even when versions carry their own.
      to: [{ email: chunk[0].email, name: chunk[0].name || undefined }],
      messageVersions: chunk.map((r) => ({ to: [{ email: r.email, name: r.name || undefined }] })),
    };
    const response = await fetch(BREVO_URL, {
      method: "POST",
      headers: { "api-key": env.email.brevoApiKey, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new Error(`Brevo ${response.status}: ${detail.slice(0, 300)}`);
    }
  }
}

function stripHtml(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

export function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/** The one layout every mail uses: dark header with the name, a white body, a gold button. */
export function layout(title: string, bodyHtml: string, button?: { label: string; url: string }): string {
  return `<!doctype html><html><body style="margin:0;background:#f4f4f5;font-family:Inter,Segoe UI,Helvetica,Arial,sans-serif;color:#0b0b0c">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:24px 0">
<tr><td align="center">
<table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden">
<tr><td style="background:#0b0b0c;padding:18px 28px;color:#ffd100;font-weight:700;font-size:18px">${APP_NAME}</td></tr>
<tr><td style="padding:28px">
<h1 style="margin:0 0 14px;font-size:20px">${escapeHtml(title)}</h1>
<div style="font-size:15px;line-height:1.55;white-space:pre-wrap">${bodyHtml}</div>
${
  button
    ? `<p style="margin:26px 0 8px"><a href="${button.url}" style="display:inline-block;background:#ffd100;color:#0b0b0c;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:999px">${escapeHtml(button.label)}</a></p>
<p style="margin:0;font-size:12px;color:#7a7a82;word-break:break-all">${button.url}</p>`
    : ""
}
</td></tr>
<tr><td style="padding:14px 28px;font-size:12px;color:#7a7a82;border-top:1px solid #ececef">${APP_NAME} · <a href="${SITE_URL}" style="color:#7a7a82">${SITE_URL.replace(/^https?:\/\//, "")}</a></td></tr>
</table></td></tr></table></body></html>`;
}

/* ───────────────────────────── The mails ─────────────────────────── */

export async function sendInvite(to: Recipient, token: string, invitedBy: string, roleLabel: string) {
  const url = `${SITE_URL}/invite/${token}`;
  await sendEmail(
    [to],
    `Your ${APP_NAME} account`,
    layout(
      `You have been added to ${APP_NAME}`,
      `${escapeHtml(invitedBy)} created a ${escapeHtml(roleLabel)} account for you.<br><br>Open the link to choose a password and set up your profile. If the ${APP_NAME} app is installed it opens there; otherwise the website does. The link works for 7 days.`,
      { label: "Set up my account", url },
    ),
  );
}

export async function sendReset(to: Recipient, token: string) {
  const url = `${SITE_URL}/reset/${token}`;
  await sendEmail(
    [to],
    `Reset your ${APP_NAME} password`,
    layout(
      "Reset your password",
      `Open the link to choose a new password. It works for 2 hours. If you did not ask for this, ignore this mail.`,
      { label: "Choose a new password", url },
    ),
  );
}

export async function sendNotice(to: Recipient[], subject: string, title: string, body: string, link?: string) {
  await sendEmail(
    to,
    subject,
    layout(title, escapeHtml(body), link ? { label: `Open in ${APP_NAME}`, url: link } : undefined),
  );
}
