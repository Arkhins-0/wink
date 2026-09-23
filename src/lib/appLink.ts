/**
 * Site paths the Android app opens itself (its App Links intent filter,
 * AndroidManifest.xml). Anything else, such as the sign-in page, opens the
 * app at Home.
 */
const APP_PATHS = [
  /^\/invite\//,
  /^\/reset\//,
  /^\/v\//,
  /^\/home(\?|$)/,
  /^\/schedule$/,
  /^\/w\//,
  /^\/chats(\/|$)/,
  /^\/people(\/|\?|$)/,
  /^\/account$/,
  /^\/archive(\/|$)/,
];

export const appPath = (path: string | null | undefined): string =>
  path && path.startsWith("/") && APP_PATHS.some((r) => r.test(path)) ? path : "/home";

/** Open this page in the app if it is installed, else download the app (see /download). */
export const openInAppHref = (path: string): string => `/download?open=${encodeURIComponent(path)}`;
