import type { Metadata } from "next";
import { LegalPage, Section } from "@/components/LegalPage";
import { CONTACT_EMAIL, OPERATOR } from "@/lib/legal";

export const metadata: Metadata = { title: "Privacy Policy", description: "What Wink collects, why, who handles it, and your rights." };

const Mail = () => (
  <a href={`mailto:${CONTACT_EMAIL}`} className="text-gold hover:underline">
    {CONTACT_EMAIL}
  </a>
);

export default function Privacy() {
  return (
    <LegalPage title="Privacy Policy">
      <p>
        Wink is a communication app for race weekends, run by {OPERATOR} (&ldquo;we&rdquo;, &ldquo;us&rdquo;). This policy explains what personal data Wink
        handles, why, who it is shared with, how long it is kept, and the rights you have under India&rsquo;s Digital Personal Data Protection Act, 2023. It
        covers the website at wink.arkhins.com and the Wink Android app.
      </p>

      <Section title="1. Who can use Wink">
        <p>
          Wink is not open to the public. Accounts are created by the organisers of an event (admins, coordinators, team managers and security heads), who
          enter your email address and your role. You then receive an email to set a password.
        </p>
      </Section>

      <Section title="2. What we collect">
        <ul className="list-disc space-y-2 pl-5">
          <li>
            <strong className="text-snow">Account details</strong> entered by the person who created your account: your email address, role, team, and who you
            report to.
          </li>
          <li>
            <strong className="text-snow">Your profile</strong>, completed when you first sign in: full name, date of birth, contact number and photo. Your
            account also has a verification code and a QR code.
          </li>
          <li>
            <strong className="text-snow">What you send</strong>: announcements, race-weekend channel posts and private chat messages, with any edits and
            deletions; documents, photos, voice notes and audio files you attach; and your location, only when you choose to share it in a message.
          </li>
          <li>
            <strong className="text-snow">Message status</strong>: when a message reached a device and when it was read. Private chats show this to the sender as
            ticks.
          </li>
          <li>
            <strong className="text-snow">Technical data</strong>: sign-in sessions (whether web or app, and when last used), the push-notification token of your
            device, and, to stop password guessing, the email address and IP address of recent sign-in attempts.
          </li>
          <li>
            <strong className="text-snow">A record of account changes</strong>: who changed a profile, a status or a race time, and when.
          </li>
        </ul>
        <p>We do not use advertising, analytics or tracking tools, and we do not sell personal data.</p>
      </Section>

      <Section title="3. What the app asks your phone for">
        <ul className="list-disc space-y-2 pl-5">
          <li>
            <strong className="text-snow">Notifications</strong>, to show messages as they arrive, and an exemption from battery optimisation so they still
            arrive while the phone sleeps.
          </li>
          <li>
            <strong className="text-snow">Storage</strong> (older Android versions only), to save documents in Downloads/Wink.
          </li>
          <li>
            <strong className="text-snow">Microphone</strong>, only while you record a voice note; <strong className="text-snow">location</strong>, only when you
            share it; <strong className="text-snow">camera</strong>, only while scanning someone&rsquo;s ID code.
          </li>
        </ul>
        <p>
          The app keeps a copy of what it shows you (your chats, announcements, channel posts, the schedule, the people list, your account, and the photos and
          voice notes in them) in its own storage on your phone, so it opens quickly and without signal. You can clear it at any time under Account → Kept on
          this phone. It is cleared automatically if another person signs in on the same phone, or if your account is banned.
        </p>
      </Section>

      <Section title="4. Why we use it">
        <ul className="list-disc space-y-2 pl-5">
          <li>To run the service: deliver messages, documents and schedule changes, and show the race schedule and countdown.</li>
          <li>To let event staff confirm who you are, by scanning your QR code or entering your verification code.</li>
          <li>To notify you by push notification and, for urgent messages, by email.</li>
          <li>To keep Wink secure: sign-in limits, session management, and a record of account changes.</li>
        </ul>
        <p>
          We process this data on the basis of your consent, which you give when you set up your account, and for the legitimate uses the Act allows. You may
          withdraw consent at any time by writing to us; your account will then be closed.
        </p>
      </Section>

      <Section title="5. Who can see your data">
        <ul className="list-disc space-y-2 pl-5">
          <li>The people you message see what you send them, your name, role and photo.</li>
          <li>The people above you in your event&rsquo;s structure, and all admins, can see your profile and account status.</li>
          <li>
            Anyone signed in to Wink who scans your QR code or types your verification code sees your photo, name, role, team and account status.
          </li>
          <li>
            Service providers who run parts of Wink for us: Vercel (hosting), Neon (database), Amazon Web Services (file storage, United States), Google Firebase
            (push notifications), Brevo (email) and GitHub (app downloads). They process data only to provide their service to us.
          </li>
        </ul>
        <p>
          Some of these providers store or process data outside India, including in the United States. We may also disclose data where Indian law requires it.
        </p>
      </Section>

      <Section title="6. How long we keep it">
        <ul className="list-disc space-y-2 pl-5">
          <li>Your account and profile are kept while your account exists.</li>
          <li>
            Messages are grouped by season. Admins can archive a season (read-only) or delete it, which permanently removes that season&rsquo;s messages.
          </li>
          <li>A private message you delete loses its text and attachment straight away, for both people.</li>
          <li>Sign-in attempt records are kept only as long as needed to limit repeated attempts.</li>
        </ul>
        <p>Emails and notifications that were already delivered cannot be recalled from the devices or inboxes that received them.</p>
      </Section>

      <Section title="7. Security">
        <p>
          Passwords are stored only as salted hashes. All traffic uses HTTPS, and files are only served to people who are signed in. No system is perfectly
          secure; if a breach affects your data, we will tell you and the Data Protection Board of India as the Act requires.
        </p>
      </Section>

      <Section title="8. Children">
        <p>
          Some people on a race weekend, such as young drivers, may be under 18. Their account may only be used with the consent of a parent or lawful
          guardian, which the organiser creating the account must obtain. We do not track or monitor children&rsquo;s behaviour or show them advertising.
        </p>
      </Section>

      <Section title="9. Your rights">
        <p>You can ask us to:</p>
        <ul className="list-disc space-y-2 pl-5">
          <li>tell you what personal data we hold about you and who it has been shared with;</li>
          <li>correct or complete it (your profile is locked after setup, so your manager, an admin or we can change it for you);</li>
          <li>erase it, and close your account;</li>
          <li>name someone to act for you if you die or become unable to act.</li>
        </ul>
        <p>
          Write to <Mail />. We will reply within 30 days. If you are not satisfied with our answer, you can complain to the Data Protection Board of India.
        </p>
      </Section>

      <Section title="10. Changes to this policy">
        <p>
          If we change this policy in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.
        </p>
      </Section>

      <Section title="11. Contact">
        <p>
          {OPERATOR} · <Mail />
        </p>
      </Section>
    </LegalPage>
  );
}
