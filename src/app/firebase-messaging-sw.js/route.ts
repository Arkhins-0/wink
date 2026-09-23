import { webFirebaseConfig } from "@/lib/firebaseWeb";

export const dynamic = "force-dynamic";

/**
 * The service worker Firebase needs for background web push, served with
 * the project's config baked in. Registered from the browser at
 * /firebase-messaging-sw.js; an empty worker when push is not set up.
 */
export function GET() {
  const config = webFirebaseConfig();
  const body = config
    ? `importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js");
firebase.initializeApp(${JSON.stringify({
        apiKey: config.apiKey,
        projectId: config.projectId,
        messagingSenderId: config.messagingSenderId,
        appId: config.appId,
      })});
const messaging = firebase.messaging();
messaging.onBackgroundMessage((payload) => {
  const d = payload.data || {};
  const n = payload.notification || {};
  self.registration.showNotification(n.title || d.title || "Wink", {
    body: n.body || d.body || "",
    icon: "/icon-512.png",
    tag: d.tag,
    data: { link: d.link || "/home" },
  });
});
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const link = (event.notification.data && event.notification.data.link) || "/home";
  event.waitUntil(clients.openWindow(link));
});
`
    : "// Push is not configured on this deployment.\n";
  return new Response(body, {
    headers: { "content-type": "application/javascript; charset=utf-8", "cache-control": "no-cache" },
  });
}
