import type { Metadata } from "next";
import { LegalPage } from "@/components/LegalPage";
import { LEGAL } from "@/lib/legalContent";

export const metadata: Metadata = { title: "Terms and Conditions", description: "The terms for using Wink." };

export default function Terms() {
  return <LegalPage doc={LEGAL.terms} />;
}
