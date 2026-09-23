import type { Metadata } from "next";
import { LegalPage } from "@/components/LegalPage";
import { LEGAL } from "@/lib/legalContent";

export const metadata: Metadata = { title: "Privacy Policy", description: "What Wink collects, why, who handles it, and your rights." };

export default function Privacy() {
  return <LegalPage doc={LEGAL.privacy} />;
}
