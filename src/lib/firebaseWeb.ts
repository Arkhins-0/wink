// No "server-only": the same shape is read on both sides. The values are
// NEXT_PUBLIC_ and not secret — a Firebase web config identifies the
// project, it does not grant access to it.

export type WebFirebaseConfig = {
  apiKey: string;
  projectId: string;
  messagingSenderId: string;
  appId: string;
  vapidKey: string;
};

/** The Firebase web app, or null until all five values are set. */
export function webFirebaseConfig(): WebFirebaseConfig | null {
  const c = {
    apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "",
    projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "",
    messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? "",
    appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "",
    vapidKey: process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY ?? "",
  };
  return Object.values(c).every(Boolean) ? c : null;
}
