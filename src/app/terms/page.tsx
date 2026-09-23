import type { Metadata } from "next";
import Link from "next/link";
import { LegalPage, Section } from "@/components/LegalPage";
import { CONTACT_EMAIL, OPERATOR } from "@/lib/legal";

export const metadata: Metadata = { title: "Terms and Conditions", description: "The terms for using Wink." };

export default function Terms() {
  return (
    <LegalPage title="Terms and Conditions">
      <p>
        These terms apply to your use of Wink, the race-weekend communication service at wink.arkhins.com and in the Wink Android app, run by {OPERATOR}{" "}
        (&ldquo;we&rdquo;, &ldquo;us&rdquo;). By setting up your account you agree to them and to our{" "}
        <Link href="/privacy" className="text-gold hover:underline">
          Privacy Policy
        </Link>
        . If you do not agree, do not set up or use an account.
      </p>

      <Section title="1. Accounts">
        <ul className="list-disc space-y-2 pl-5">
          <li>Wink is by invitation only. Your account is created by an organiser of your event, who decides your role.</li>
          <li>Keep your password to yourself. You are responsible for what is sent from your account.</li>
          <li>
            The profile you complete when you first sign in must be accurate: name, date of birth, contact number and a photo that shows you. It is used to
            confirm who you are at the venue. After setup it can only be changed by your manager or an admin.
          </li>
          <li>If you are under 18, a parent or lawful guardian must agree to these terms for you.</li>
        </ul>
      </Section>

      <Section title="2. Using Wink">
        <p>Use Wink for communication about your event. Do not:</p>
        <ul className="list-disc space-y-2 pl-5">
          <li>send anything unlawful, abusive, harassing, discriminatory, or that you have no right to share;</li>
          <li>share other people&rsquo;s personal data or confidential event information outside the people who need it;</li>
          <li>use someone else&rsquo;s account, QR code or verification code, or try to get into parts of Wink you are not given;</li>
          <li>upload malware, or try to disrupt, overload or reverse-engineer the service;</li>
          <li>mark messages urgent without need: urgent messages also go out by email.</li>
        </ul>
      </Section>

      <Section title="3. Not a safety system">
        <p>
          Wink carries messages as quickly as it can, but delivery depends on networks, phone settings and third-party services such as Google&rsquo;s
          notification service. Do not rely on Wink for emergencies, race control or any safety-critical instruction. Always follow the official procedures and
          channels of your event.
        </p>
      </Section>

      <Section title="4. What you send">
        <ul className="list-disc space-y-2 pl-5">
          <li>
            You remain responsible for your messages and files. You let us store and deliver them to the people you send them to, which is all we use them for.
          </li>
          <li>
            You can edit or delete a private chat message for two hours after sending it. Deleting removes it for both people, but a notification or email that
            was already delivered cannot be recalled.
          </li>
          <li>Organisers can archive or delete a whole season of messages.</li>
        </ul>
      </Section>

      <Section title="5. Account status">
        <p>
          Organisers and admins can suspend, dismiss or ban an account, for example when someone leaves the event or breaks these terms. A suspended or dismissed
          account cannot sign in. When an account is banned, the app also removes that person&rsquo;s chats from their phone.
        </p>
      </Section>

      <Section title="6. The app">
        <p>
          The Android app is offered as a download from our site and GitHub, and updates itself from there. Keep it up to date: older versions may stop working
          with the service.
        </p>
      </Section>

      <Section title="7. Availability and liability">
        <p>
          Wink is provided as it is, and we do not promise it will always be available or free of errors. To the extent Indian law allows, {OPERATOR} is not
          liable for indirect or consequential loss, or for loss caused by a message that was delayed, not delivered, or misread. Nothing in these terms limits
          liability that cannot be limited by law.
        </p>
      </Section>

      <Section title="8. Ending your use">
        <p>
          You can stop using Wink at any time and ask us to close your account by writing to{" "}
          <a href={`mailto:${CONTACT_EMAIL}`} className="text-gold hover:underline">
            {CONTACT_EMAIL}
          </a>
          . We may suspend or close an account that breaks these terms.
        </p>
      </Section>

      <Section title="9. Changes">
        <p>If we change these terms in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.</p>
      </Section>

      <Section title="10. Law">
        <p>These terms are governed by the laws of India, and the courts in India have jurisdiction over any dispute about them.</p>
      </Section>

      <Section title="11. Contact">
        <p>
          {OPERATOR} ·{" "}
          <a href={`mailto:${CONTACT_EMAIL}`} className="text-gold hover:underline">
            {CONTACT_EMAIL}
          </a>
        </p>
      </Section>
    </LegalPage>
  );
}
