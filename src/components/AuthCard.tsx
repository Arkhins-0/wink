import Image from "next/image";
import Link from "next/link";
import { APP_NAME } from "@/lib/config";

/** The frame for every page a signed-out person sees: the mark on the left on a laptop, the form on the right. */
export function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <main className="flex min-h-screen">
      <section className="relative hidden flex-1 flex-col justify-between overflow-hidden border-r border-night-line bg-night-panel/40 p-10 lg:flex">
        <div className="flex items-center gap-3">
          <Image src="/ctr-logo.png" alt="" width={56} height={32} priority />
          <span className="text-2xl font-bold tracking-tight">{APP_NAME}</span>
        </div>
        <div className="max-w-md">
          <h2 className="text-4xl font-bold leading-tight tracking-tight">Race weekend, one channel.</h2>
          <p className="mt-4 text-snow-soft">Announcements, documents, schedule changes and chats — straight from the people running the weekend to the people on the ground.</p>
        </div>
        <p className="text-xs text-snow-faint">wink.arkhins.com</p>
        <div className="pointer-events-none absolute -right-32 -top-32 h-96 w-96 rounded-full bg-gold/10 blur-3xl" />
      </section>
      <section className="flex flex-1 flex-col items-center justify-center px-5 py-10">
        <div className="mb-8 flex items-center gap-3 lg:hidden">
          <Image src="/ctr-logo.png" alt="" width={56} height={32} priority />
          <span className="text-2xl font-bold tracking-tight">{APP_NAME}</span>
        </div>
        <div className="card w-full max-w-sm">
          <h1 className="mb-5 text-lg font-semibold">{title}</h1>
          {children}
        </div>
        <p className="mt-6 flex gap-4 text-xs text-snow-faint">
          <Link href="/privacy" className="hover:text-snow">
            Privacy Policy
          </Link>
          <Link href="/terms" className="hover:text-snow">
            Terms and Conditions
          </Link>
        </p>
      </section>
    </main>
  );
}
