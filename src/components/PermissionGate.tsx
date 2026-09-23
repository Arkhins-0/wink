"use client";

import { useEffect, useState } from "react";
import { APP_NAME } from "@/lib/config";

type State = "checking" | "granted" | "prompt" | "denied" | "unsupported";

/**
 * Nothing shows until notifications are allowed. Denied is not the end:
 * a warning explains why and asks again. Browsers that cannot do
 * notifications at all (some phones outside an installed web app) get a
 * one-line notice instead of a locked door.
 */
export function PermissionGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<State>("checking");
  const [dismissedUnsupported, setDismissedUnsupported] = useState(false);

  useEffect(() => {
    if (typeof Notification === "undefined") return setState("unsupported");
    setState(Notification.permission === "granted" ? "granted" : Notification.permission === "denied" ? "denied" : "prompt");
  }, []);

  const ask = async () => {
    try {
      const result = await Notification.requestPermission();
      setState(result === "granted" ? "granted" : "denied");
    } catch {
      setState("denied");
    }
  };

  if (state === "checking") return null;
  if (state === "granted" || (state === "unsupported" && dismissedUnsupported)) return <>{children}</>;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-night px-5">
      <div className="card w-full max-w-sm text-center">
        <h1 className="text-lg font-semibold">Allow notifications</h1>
        {state === "unsupported" ? (
          <>
            <p className="mt-2 text-sm text-snow-soft">
              This browser cannot show notifications. Install the {APP_NAME} app to get race alerts as popups.
            </p>
            <button className="btn-gold mt-5 w-full" onClick={() => setDismissedUnsupported(true)}>
              Continue
            </button>
          </>
        ) : (
          <>
            <p className="mt-2 text-sm text-snow-soft">
              {APP_NAME} delivers race updates and documents as popups. It only works with notifications allowed.
            </p>
            {state === "denied" && (
              <p className="error mt-4 text-left">
                Notifications are blocked. Allow them for this site in your browser settings (the lock icon next to the
                address), then try again.
              </p>
            )}
            <button className="btn-gold mt-5 w-full" onClick={ask}>
              {state === "denied" ? "Try again" : "Allow notifications"}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
