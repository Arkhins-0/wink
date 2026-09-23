import Image from "next/image";
import Link from "next/link";
import { Fragment } from "react";
import { APP_NAME } from "@/lib/config";
import type { Block, LegalDoc } from "@/lib/legalContent";

/** **bold** and [label](href) inside a line of legal text. */
function Inline({ text }: { text: string }) {
  const parts = text.split(/(\*\*[^*]+\*\*|\[[^\]]+\]\([^)]+\))/g);
  return (
    <>
      {parts.map((part, i) => {
        const bold = /^\*\*([^*]+)\*\*$/.exec(part);
        if (bold) return <strong key={i} className="text-snow">{bold[1]}</strong>;
        const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
        if (link) {
          return link[2].startsWith("/") ? (
            <Link key={i} href={link[2]} className="text-gold hover:underline">
              {link[1]}
            </Link>
          ) : (
            <a key={i} href={link[2]} className="text-gold hover:underline">
              {link[1]}
            </a>
          );
        }
        return <Fragment key={i}>{part}</Fragment>;
      })}
    </>
  );
}

function Blocks({ blocks }: { blocks: Block[] }) {
  return (
    <>
      {blocks.map((b, i) =>
        "p" in b ? (
          <p key={i}>
            <Inline text={b.p} />
          </p>
        ) : (
          <ul key={i} className="list-disc space-y-2 pl-5">
            {b.ul.map((item, j) => (
              <li key={j}>
                <Inline text={item} />
              </li>
            ))}
          </ul>
        ),
      )}
    </>
  );
}

/** A legal document as a page: the mark, the title and date, the sections, and links between the two documents. */
export function LegalPage({ doc }: { doc: LegalDoc }) {
  return (
    <main className="mx-auto max-w-3xl px-5 py-10 sm:py-14">
      <Link href="/" className="mb-10 inline-flex items-center gap-3">
        <Image src="/ctr-logo.png" alt="" width={48} height={28} priority />
        <span className="text-xl font-bold tracking-tight">{APP_NAME}</span>
      </Link>
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{doc.title}</h1>
      <p className="mt-2 text-sm text-snow-faint">Last updated {doc.updated}</p>
      <div className="mt-8 space-y-8 text-sm leading-relaxed text-snow-soft sm:text-base">
        <Blocks blocks={doc.intro} />
        {doc.sections.map((s) => (
          <section key={s.title} className="space-y-3">
            <h2 className="text-lg font-semibold text-snow">{s.title}</h2>
            <Blocks blocks={s.blocks} />
          </section>
        ))}
      </div>
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
