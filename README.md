# Wink

One repository, two halves:

| Path        | What it is | Deploys to |
|-------------|------------|------------|
| `/` (root)  | The website and API, Next.js. | `https://wink.arkhins.com` |
| `android/`  | The Android app, Kotlin + Jetpack Compose. See [android/README.md](android/README.md). | GitHub Releases, then into installed apps through the in-app update popup |

Neither has anything inside yet. This is the base: the app is version
`0.0.0.0` and knows how to update itself; the site serves the endpoint the
app checks, a download link, and is wired to the database and storage in
`.env`, ready for whatever comes next.

## Website

Requirements: Node 20 or newer.

```bash
npm install
npm run dev        # http://localhost:3000
npm run build      # production build
npm start          # serve the production build
npm run typecheck
```

### Routes

| Route | What it does |
|---|---|
| `/` | Landing page: the CTR mark, the name, a download button and the latest version. |
| `/download` | Redirects to the latest release APK on GitHub (or the releases page if there is none yet). The one link to share. |
| `/api/app-version` | `{ version, releaseUrl, apkUrl, notes }` for the latest GitHub release, or 404 when none is published yet. The Android app calls this at launch. `?fresh=1` bypasses the half-hour cache. |
| `/api/health` | Whether the database and storage are configured; `?deep=1` also pings the database. |

### Environment

`.env` at the root holds everything; `.env.example` shows the shape. It is
gitignored: set the same keys in the host's environment (Vercel → Project →
Settings → Environment Variables) when deploying.

| Key | Used by | What it is |
|---|---|---|
| `DATABASE_URL`, `DATABASE_URL_POOLED` | site (`src/lib/db.ts`) | Neon Postgres. The pooled URL is preferred when set. |
| `AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_BUCKET` | site (`src/lib/storage.ts`) | S3-compatible object storage. Without them the site falls back to a local `data/` folder. |
| `S3_PREFIX` | site | Folder at the bucket root everything sits under. Default `wink`. |
| `WINK_GITHUB_REPO` | site and app | `owner/name` whose Releases carry the APKs. |
| `GITHUB_TOKEN` | site (optional) | Raises GitHub's rate limit for release checks. |
| `NEXT_PUBLIC_SITE_URL` | site | Public URL for metadata. Default `https://wink.arkhins.com`. |
| `WINK_BASE_URL`, `WINK_UPDATE_URL` | app only | Where the app looks for its update endpoint. |

The database and storage secrets are read only by the website. The Android
build reads only the `WINK_*` keys (see `android/app/build.gradle.kts`).

### Deploying to wink.arkhins.com

The site is a standard Next.js app; Vercel is the zero-config path:

1. Import the repository in Vercel. Root directory: the repository root.
   `vercel.json` tells it to skip deployments that only touch `android/`.
2. Add the environment variables above.
3. Add the domain `wink.arkhins.com` to the project and point its DNS
   (a CNAME to `cname.vercel-dns.com`) at Vercel.

Any host that runs `npm run build && npm start` with the same environment
variables works too.

### Code layout

```
src/
  app/
    page.tsx                 landing page
    layout.tsx               fonts, metadata, theme colour
    globals.css              Tailwind base and the few shared classes
    icon.png, apple-icon.png favicons (from the CTR mark)
    download/route.ts        302 to the latest APK
    api/app-version/route.ts the update endpoint the app calls
    api/health/route.ts      configuration check
  lib/
    config.ts                app name, site URL, credit (safe for client components)
    env.ts                   every environment variable, read once (server only)
    db.ts                    Postgres pool: q / one / run / tx / ping
    storage.ts               S3 (or local disk): put / get / exists / remove / list
    appReleases.ts           latest GitHub release, cached 30 minutes
    http.ts                  json() and fail() helpers for route handlers
public/
  ctr-logo.png               the CTR mark
  og.png, icon-512.png       share image and icon
android/                     the app; see its README
.github/workflows/release.yml  tag v* → build, sign and publish the APKs
```

## Releasing the app

Bump the version in `android/app/build.gradle.kts`, commit, then tag and
push (`git tag v0.0.0.1 && git push origin v0.0.0.1`). The workflow builds
and publishes the APKs to a GitHub Release; the site's `/api/app-version`
picks it up within half an hour, and installed apps show the update popup
on their next launch. Details, including the signing secrets the workflow
needs, are in [android/README.md](android/README.md).
