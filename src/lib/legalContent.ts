import { CONTACT_EMAIL, OPERATOR, TERMS_UPDATED, TERMS_VERSION } from "./legal";

/*
 * The Privacy Policy and the Terms, written once: the website renders them
 * as pages, and /api/legal/<doc> gives them to the app, which shows them on
 * its own screen and keeps a copy. Text may carry **bold** and
 * [links](href); an href of /privacy or /terms points at the other document.
 */

export type Block = { p: string } | { ul: string[] };
export type LegalSection = { title: string; blocks: Block[] };
export type LegalDoc = { title: string; updated: string; version: string; intro: Block[]; sections: LegalSection[] };
export type LegalKey = "privacy" | "terms";

const mail = `[${CONTACT_EMAIL}](mailto:${CONTACT_EMAIL})`;

const privacy: LegalDoc = {
  title: "Privacy Policy",
  updated: TERMS_UPDATED,
  version: TERMS_VERSION,
  intro: [
    {
      p: `Wink is a communication app for race weekends, run by ${OPERATOR} (“we”, “us”). This policy explains what personal data Wink handles, why, who it is shared with, how long it is kept, and the rights you have under India’s Digital Personal Data Protection Act, 2023. It covers the website at wink.arkhins.com and the Wink Android app.`,
    },
  ],
  sections: [
    {
      title: "1. Who can use Wink",
      blocks: [
        {
          p: "Wink is not open to the public. Accounts are created by the organisers of an event (admins, coordinators, team managers and security heads), who enter your email address and your role. You then receive an email to set a password.",
        },
      ],
    },
    {
      title: "2. What we collect",
      blocks: [
        {
          ul: [
            "**Account details** entered by the person who created your account: your email address, role, team, and who you report to.",
            "**Your profile**, completed when you first sign in: full name, date of birth, contact number and photo. Your account also has a verification code and a QR code.",
            "**What you send**: announcements, race-weekend channel posts and private chat messages, with any edits and deletions; documents, photos, voice notes and audio files you attach; and your location, only when you choose to share it in a message.",
            "**Message status**: when a message reached a device and when it was read. Private chats show this to the sender as ticks.",
            "**Technical data**: sign-in sessions (whether web or app, and when last used), the push-notification token of your device, and, to stop password guessing, the email address and IP address of recent sign-in attempts.",
            "**A record of account changes**: who changed a profile, a status or a race time, and when.",
          ],
        },
        { p: "We do not use advertising, analytics or tracking tools, and we do not sell personal data." },
      ],
    },
    {
      title: "3. What the app asks your phone for",
      blocks: [
        {
          ul: [
            "**Notifications**, to show messages as they arrive, and an exemption from battery optimisation so they still arrive while the phone sleeps.",
            "**Storage** (older Android versions only), to save documents in Downloads/Wink.",
            "**Microphone**, only while you record a voice note; **location**, only when you share it; **camera**, only while scanning someone’s ID code.",
          ],
        },
        {
          p: "The app keeps a copy of what it shows you (your chats, announcements, channel posts, the schedule, the people list, your account, these documents, and the photos and voice notes in them) in its own storage on your phone, so it opens quickly and without signal. You can clear it at any time under Account → Kept on this phone. It is cleared automatically if another person signs in on the same phone, or if your account is banned.",
        },
      ],
    },
    {
      title: "4. Why we use it",
      blocks: [
        {
          ul: [
            "To run the service: deliver messages, documents and schedule changes, and show the race schedule and countdown.",
            "To let event staff confirm who you are, by scanning your QR code or entering your verification code.",
            "To notify you by push notification and, for urgent messages, by email.",
            "To keep Wink secure: sign-in limits, session management, and a record of account changes.",
          ],
        },
        {
          p: "We process this data on the basis of your consent, which you give when you set up your account, and for the legitimate uses the Act allows. You may withdraw consent at any time by writing to us; your account will then be closed.",
        },
      ],
    },
    {
      title: "5. Who can see your data",
      blocks: [
        {
          ul: [
            "The people you message see what you send them, your name, role and photo.",
            "The people above you in your event’s structure, and all admins, can see your profile and account status.",
            "Anyone signed in to Wink who scans your QR code or types your verification code sees your photo, name, role, team and account status.",
            "Service providers who run parts of Wink for us: Vercel (hosting), Neon (database), Amazon Web Services (file storage, United States), Google Firebase (push notifications), Brevo (email) and GitHub (app downloads). They process data only to provide their service to us.",
          ],
        },
        {
          p: "Some of these providers store or process data outside India, including in the United States. We may also disclose data where Indian law requires it.",
        },
      ],
    },
    {
      title: "6. How long we keep it",
      blocks: [
        {
          ul: [
            "Your account and profile are kept while your account exists.",
            "Messages are grouped by season. Admins can archive a season (read-only) or delete it, which permanently removes that season’s messages.",
            "A private message you delete loses its text and attachment straight away, for both people.",
            "Sign-in attempt records are kept only as long as needed to limit repeated attempts.",
          ],
        },
        { p: "Emails and notifications that were already delivered cannot be recalled from the devices or inboxes that received them." },
      ],
    },
    {
      title: "7. Security",
      blocks: [
        {
          p: "Passwords are stored only as salted hashes. All traffic uses HTTPS, and files are only served to people who are signed in. No system is perfectly secure; if a breach affects your data, we will tell you and the Data Protection Board of India as the Act requires.",
        },
      ],
    },
    {
      title: "8. Children",
      blocks: [
        {
          p: "Some people on a race weekend, such as young drivers, may be under 18. Their account may only be used with the consent of a parent or lawful guardian, which the organiser creating the account must obtain. We do not track or monitor children’s behaviour or show them advertising.",
        },
      ],
    },
    {
      title: "9. Your rights",
      blocks: [
        { p: "You can ask us to:" },
        {
          ul: [
            "tell you what personal data we hold about you and who it has been shared with;",
            "correct or complete it (your profile is locked after setup, so your manager, an admin or we can change it for you);",
            "erase it, and close your account;",
            "name someone to act for you if you die or become unable to act.",
          ],
        },
        {
          p: `Write to ${mail}. We will reply within 30 days. If you are not satisfied with our answer, you can complain to the Data Protection Board of India.`,
        },
      ],
    },
    {
      title: "10. Changes to this policy",
      blocks: [
        {
          p: "If we change this policy in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.",
        },
      ],
    },
    { title: "11. Contact", blocks: [{ p: `${OPERATOR} · ${mail}` }] },
  ],
};

const terms: LegalDoc = {
  title: "Terms and Conditions",
  updated: TERMS_UPDATED,
  version: TERMS_VERSION,
  intro: [
    {
      p: `These terms apply to your use of Wink, the race-weekend communication service at wink.arkhins.com and in the Wink Android app, run by ${OPERATOR} (“we”, “us”). By setting up your account you agree to them and to our [Privacy Policy](/privacy). If you do not agree, do not set up or use an account.`,
    },
  ],
  sections: [
    {
      title: "1. Accounts",
      blocks: [
        {
          ul: [
            "Wink is by invitation only. Your account is created by an organiser of your event, who decides your role.",
            "Keep your password to yourself. You are responsible for what is sent from your account.",
            "The profile you complete when you first sign in must be accurate: name, date of birth, contact number and a photo that shows you. It is used to confirm who you are at the venue. After setup it can only be changed by your manager or an admin.",
            "If you are under 18, a parent or lawful guardian must agree to these terms for you.",
          ],
        },
      ],
    },
    {
      title: "2. Using Wink",
      blocks: [
        { p: "Use Wink for communication about your event. Do not:" },
        {
          ul: [
            "send anything unlawful, abusive, harassing, discriminatory, or that you have no right to share;",
            "share other people’s personal data or confidential event information outside the people who need it;",
            "use someone else’s account, QR code or verification code, or try to get into parts of Wink you are not given;",
            "upload malware, or try to disrupt, overload or reverse-engineer the service;",
            "mark messages urgent without need: urgent messages also go out by email.",
          ],
        },
      ],
    },
    {
      title: "3. Not a safety system",
      blocks: [
        {
          p: "Wink carries messages as quickly as it can, but delivery depends on networks, phone settings and third-party services such as Google’s notification service. Do not rely on Wink for emergencies, race control or any safety-critical instruction. Always follow the official procedures and channels of your event.",
        },
      ],
    },
    {
      title: "4. What you send",
      blocks: [
        {
          ul: [
            "You remain responsible for your messages and files. You let us store and deliver them to the people you send them to, which is all we use them for.",
            "You can edit or delete a private chat message for two hours after sending it. Deleting removes it for both people, but a notification or email that was already delivered cannot be recalled.",
            "Organisers can archive or delete a whole season of messages.",
          ],
        },
      ],
    },
    {
      title: "5. Account status",
      blocks: [
        {
          p: "Organisers and admins can suspend, dismiss or ban an account, for example when someone leaves the event or breaks these terms. A suspended or dismissed account cannot sign in. When an account is banned, the app also removes that person’s chats from their phone.",
        },
      ],
    },
    {
      title: "6. The app",
      blocks: [
        {
          p: "The Android app is offered as a download from our site and GitHub, and updates itself from there. Keep it up to date: older versions may stop working with the service.",
        },
      ],
    },
    {
      title: "7. Availability and liability",
      blocks: [
        {
          p: `Wink is provided as it is, and we do not promise it will always be available or free of errors. To the extent Indian law allows, ${OPERATOR} is not liable for indirect or consequential loss, or for loss caused by a message that was delayed, not delivered, or misread. Nothing in these terms limits liability that cannot be limited by law.`,
        },
      ],
    },
    {
      title: "8. Ending your use",
      blocks: [
        {
          p: `You can stop using Wink at any time and ask us to close your account by writing to ${mail}. We may suspend or close an account that breaks these terms.`,
        },
      ],
    },
    {
      title: "9. Changes",
      blocks: [
        {
          p: "If we change these terms in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.",
        },
      ],
    },
    {
      title: "10. Law",
      blocks: [{ p: "These terms are governed by the laws of India, and the courts in India have jurisdiction over any dispute about them." }],
    },
    { title: "11. Contact", blocks: [{ p: `${OPERATOR} · ${mail}` }] },
  ],
};

export const LEGAL: Record<LegalKey, LegalDoc> = { privacy, terms };

export const isLegalKey = (v: string): v is LegalKey => v === "privacy" || v === "terms";
