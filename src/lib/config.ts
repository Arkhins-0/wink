// No "server-only" here and no Node built-ins: this module may be imported
// by client components too. Server-only values live in env.ts.

export const APP_NAME = "Wink";

/** Where this site is deployed. Used for absolute links and metadata. */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || "https://wink.arkhins.com").replace(/\/+$/, "");

export const POWERED_BY = { name: "arkhins.com", url: "https://arkhins.com" };
