import Image from "next/image";
import Link from "next/link";
import { APP_NAME } from "@/lib/config";
import { TERMS_UPDATED } from "@/lib/legal";

/** A page of legal text: the mark, the title and date, the sections, and links between the two documents. */
export function LegalPage({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-3xl px-5 py-10 sm:py-14">
      <Link href="/" className="mb-10 inline-flex items-center gap-3">
        <Image src="/ctr-logo.png" alt="" width={48} height={28} priority />
        <span className="text-xl font-bold tracking-tight">{APP_NAME}</span>
      </Link>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
      <p className="mt-2 text-sm text-snow-faint">Last updated {TERMS_UPDATED}</p>
      <div className="legal mt-8 space-y-8 text-sm leading-relaxed text-snow-soft sm:text-base">{children}</div>
      <nav className="mt-12 flex flex-wrap gap-x-5 gap-y-2 border-t border-night-line pt-6 text-sm text-snow-faint">
        <Link href="/privacy" className="hover:text-snow">
          Privacy Policy
        </Link>
        <Link href="/terms" className="hover:text-snow">
          Terms and Conditions
        </Link>
        <Link href="/" className="hover:text-snow">
          Sign in
        </Link>
      </nav>
    </main>
  );
}

/** One numbered part of a legal page. */
export function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-snow">{title}</h2>
      {children}
    </section>
  );
}
