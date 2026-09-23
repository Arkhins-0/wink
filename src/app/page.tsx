import Image from "next/image";
import { APP_NAME, POWERED_BY } from "@/lib/config";
import { latestRelease, releasesPage } from "@/lib/appReleases";

// The release is looked up on every request (and cached in memory for half
// an hour), never baked in at build time.
export const dynamic = "force-dynamic";

export default async function Home() {
  const release = await latestRelease();

  return (
    <main className="container-x flex min-h-screen flex-col">
      <header className="flex items-center justify-between py-6">
        <div className="flex items-center gap-3">
          <Image src="/ctr-logo.png" alt="CTR" width={44} height={25} priority />
          <span className="text-lg font-semibold tracking-tight">{APP_NAME}</span>
        </div>
        <a href="/download" className="btn-ghost text-xs">
          Download
        </a>
      </header>

      <section className="flex flex-1 flex-col items-center justify-center py-16 text-center">
        <div className="shadow-glow rounded-3xl">
          <Image src="/ctr-logo.png" alt="CTR" width={260} height={148} priority />
        </div>
        <h1 className="mt-10 text-5xl font-bold tracking-tight sm:text-6xl">{APP_NAME}</h1>
        <p className="mt-4 max-w-md text-snow-soft">Coming soon. The Android app is on its way.</p>

        <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row">
          <a href="/download" className="btn-gold">
            Get the Android app
          </a>
          <a href={release?.releaseUrl ?? releasesPage()} className="btn-ghost" rel="noreferrer">
            All releases
          </a>
        </div>

        <p className="mt-6 text-xs text-snow-faint">
          {release ? `Latest version v${release.version}` : "No release published yet"}
        </p>
      </section>

      <footer className="flex items-center justify-between py-6 text-xs text-snow-faint">
        <span>© {new Date().getFullYear()} {APP_NAME}</span>
        <a href={POWERED_BY.url} className="hover:text-snow" rel="noreferrer">
          Powered by {POWERED_BY.name}
        </a>
      </footer>
    </main>
  );
}
