/** @type {import("next").NextConfig} */

// React Fast Refresh needs eval in development; production never gets it.
const dev = process.env.NODE_ENV !== "production";

// Browsers upload documents straight to the bucket, so its origin must be
// allowed for fetch. Blank (local disk) adds nothing.
const s3Origin = (() => {
  try {
    return process.env.AWS_ENDPOINT_URL_S3 ? new URL(process.env.AWS_ENDPOINT_URL_S3).origin : "";
  } catch {
    return "";
  }
})();

/*
 * Security headers for every response. Scripts come from this origin (plus
 * Google's CDN for the Firebase service worker), connections go to this
 * origin, Firebase Cloud Messaging and the storage bucket, and the only
 * frames are the in-page document viewer and Microsoft's Office viewer.
 */
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline' https://www.gstatic.com${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src 'self' https://fcmregistrations.googleapis.com https://firebaseinstallations.googleapis.com https://fcm.googleapis.com https://www.gstatic.com${s3Origin ? ` ${s3Origin}` : ""}`,
  "frame-src 'self' https://view.officeapps.live.com",
  "worker-src 'self'",
  "media-src 'self' blob:",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

const nextConfig = {
  reactStrictMode: true,
  // Required at run time rather than bundled: pg reads its native-optional bindings.
  serverExternalPackages: ["pg", "firebase-admin"],
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          // Camera for the QR scanner, microphone for voice notes, location for sharing where you are.
          { key: "Permissions-Policy", value: "camera=(self), microphone=(self), geolocation=(self)" },
        ],
      },
      {
        // The in-page viewer frames a file from this same site. Only same-origin: no other site may.
        source: "/api/files/:id/content",
        headers: [
          { key: "Content-Security-Policy", value: csp.replace("frame-ancestors 'none'", "frame-ancestors 'self'") },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
        ],
      },
    ];
  },
};

export default nextConfig;
