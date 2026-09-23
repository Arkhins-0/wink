"use client";

import Image from "next/image";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { openInAppHref } from "@/lib/appLink";

const CHOSE_WEB = "wink:continue-in-web";
const QUIET = ["/download", "/privacy", "/terms"];

/**
 * On an Android phone's browser: open this page in the app (installed →
 * the app opens here; not installed → the app downloads), or carry on in
 * the browser, which is remembered until the browser is closed. Not shown
 * in the installed web app, on laptops, or on the legal pages.
 */
export function AppChooser() {
  const pathname = usePathname();
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (!/android/i.test(navigator.userAgent)) return;
    if (window.matchMedia("(display-mode: standalone)").matches) return;
    if (QUIET.some((p) => pathname.startsWith(p))) return;
    try {
      if (sessionStorage.getItem(CHOSE_WEB) === "1") return;
    } catch {
      // No storage: ask every time.
    }
    setShow(true);
  }, [pathname]);

  if (!show) return null;

  const continueInWeb = () => {
    try {
      sessionStorage.setItem(CHOSE_WEB, "1");
    } catch {
      // Nothing to remember it in.
    }
    setShow(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-night/70 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="app-chooser-title">
      <div className="w-full rounded-t-3xl border-t border-night-line bg-night-panel px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-6 shadow-card">
        <div className="mb-5 flex items-center gap-3">
          <Image src="/icon-512.png" alt="" width={48} height={48} className="rounded-xl" />
          <div>
            <h2 id="app-chooser-title" className="text-base font-semibold">
              Open in the Wink app?
            </h2>
            <p className="text-sm text-snow-faint">Messages arrive faster, and it works without signal.</p>
          </div>
        </div>
        <a href={openInAppHref(pathname + window.location.search)} className="btn-gold block w-full text-center">
          Open the app
        </a>
        <p className="mt-2 text-center text-xs text-snow-faint">Not installed yet? It downloads instead.</p>
        <button type="button" className="btn-ghost mt-3 w-full" onClick={continueInWeb}>
          Continue in the browser
        </button>
      </div>
    </div>
  );
}
