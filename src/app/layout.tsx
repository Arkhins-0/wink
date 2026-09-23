import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import { APP_NAME, SITE_URL } from "@/lib/config";
import "./globals.css";

const body = Inter({ subsets: ["latin"], display: "swap", variable: "--font-body" });

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: APP_NAME, template: `%s · ${APP_NAME}` },
  description: "Race-weekend communication: messages, documents and the schedule, delivered down the hierarchy.",
  manifest: "/manifest.webmanifest",
  applicationName: APP_NAME,
  appleWebApp: { capable: true, statusBarStyle: "black-translucent", title: APP_NAME },
  formatDetection: { telephone: false },
  openGraph: {
    title: APP_NAME,
    description: "Race-weekend communication for the team.",
    url: SITE_URL,
    siteName: APP_NAME,
    images: [{ url: "/og.png", width: 1200, height: 1200 }],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  interactiveWidget: "resizes-content",
  themeColor: "#0B0B0C",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={body.variable}>
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
