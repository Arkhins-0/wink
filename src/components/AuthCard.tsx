import Image from "next/image";
import { APP_NAME } from "@/lib/config";

/** The frame for every page a signed-out person sees. */
export function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-5 py-10">
      <div className="mb-8 flex items-center gap-3">
        <Image src="/ctr-logo.png" alt="" width={56} height={32} priority />
        <span className="text-2xl font-bold tracking-tight">{APP_NAME}</span>
      </div>
      <div className="card w-full max-w-sm">
        <h1 className="mb-5 text-lg font-semibold">{title}</h1>
        {children}
      </div>
    </main>
  );
}
