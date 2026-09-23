# Wink

Race-weekend communication for one championship: messages and documents flow
**down** a fixed hierarchy, arrive as popups (push) and — when urgent — email,
and everyone sees the next race and its countdown.

One repository, two halves:

| Path        | What it is | Deploys to |
|-------------|------------|------------|
| `/` (root)  | The website and API, Next.js 15. | `https://wink.arkhins.com` (Vercel) |
| `android/`  | The Android app, Kotlin + Jetpack Compose. See [android/README.md](android/README.md). | GitHub Releases, then into installed apps through the in-app update popup |

## How it works

**Hierarchy.** Admins create admins and coordinators. Coordinators create race
officials, team managers, security heads and volunteers (a volunteer belongs to
the coordinator who created them; an admin can move them). Team managers create
drivers and crew (who inherit the team). Security heads create security.
Everyone can see, message and email only the people below them
(`src/lib/hierarchy.ts`).

**Accounts.** Creating a person sends an email with a link. On a phone with the
app installed the link opens the app (Android App Links, verified through
`public/.well-known/assetlinks.json`); otherwise the website. The person chooses
a password, then fills in their profile once — photo, name, date of birth,
contact number. After that the profile is locked: only their direct manager or
an admin can change it (a team manager's profile: admin only). Every account
has a QR code, an 8-character code (`XXXX-XXXX`) and a status: pending, active,
suspended, dismissed, banned. Anything but active locks the person out.

**Messages.** Three kinds, all landing in the recipient's inbox: a broadcast to
chosen people below you; a post in a race weekend's channel (admins and
coordinators post, everyone reads); a private chat a superior opens with one
person below them (both can then write in it). Messages can carry a document.
Delivery is push (Firebase) plus, when the message is urgent or carries a
document, email — except to volunteers and security, who never get automatic
email. Coordinators forward to them with the "Email volunteers" / "Email
security" buttons under People; admins email everyone with role checkboxes.

**Schedule.** Admins create race weekends and their sessions (times entered in
the track's time zone). Any change to a session goes out to everyone as urgent.
The chip at the top right of every screen counts down to the next session and
says LIVE while one runs; tapping it opens the weekend.

**Documents.** In the app, opening a document saves it to `Downloads/Wink` at
the same time; PDFs read in the app, other files open with whatever app handles
them. On the website a document offers Download or View (PDF and images in the
page, Office files through Microsoft's viewer).

**Permissions.** The app asks for notifications (and, before Android 10,
storage) on first launch and does not continue until both are allowed; a
refusal shows a warning and asks again.

## Website

Requirements: Node 20 or newer.

```bash
npm install
npm run migrate                       # apply db/migrations/*.sql to DATABASE_URL
npm run create-admin -- you@example.com   # first admin: prints (and emails) the invite link
npm run dev                           # http://localhost:3000
npm run build && npm start            # production
npm run typecheck
```

### Routes

Pages (signed in): `/home` inbox · `/schedule` · `/w/[id]` race weekend and
channel · `/chats`, `/chats/[id]` · `/compose` · `/people`, `/people/new`,
`/people/[id]`, `/people/email` · `/account` (QR, code, scanner, password) ·
`/v/[token]` verification. Signed out: `/` sign in, `/forgot`,
`/invite/[token]`, `/reset/[token]`, `/onboarding`.

API (`src/app/api/`): `auth/*`, `me`, `me/profile`, `users`, `users/[id]`
(+`/photo`, `/invite`), `verify`, `messages` (+`/[id]`, `/read`, `/unseen`),
`conversations` (+`/[id]`), `files` (+`/[id]`, `/[id]/content`,
`/[id]/ready`), `weekends` (+`/[id]`, `/[id]/sessions`, `/[id]/channel`),
`next-race`, `email/relay`, `email/bulk`, `push/register`, `config`,
`app-version`, `health`. The Android app uses the same API with a bearer token.

### Environment

`.env` at the root holds everything; `.env.example` shows the shape. It is
gitignored: set the same keys in Vercel → Project → Settings → Environment
Variables.

| Key | What it is |
|---|---|
| `DATABASE_URL`, `DATABASE_URL_POOLED` | Neon Postgres. The pooled URL is preferred when set. |
| `AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_BUCKET`, `S3_PREFIX` | Object storage for documents and photos. Browsers and the app upload straight to the bucket through signed URLs, so the bucket's CORS must allow `PUT` from `https://wink.arkhins.com`; uploads under 4 MB fall back to going through the server if it does not. |
| `EMAIL_PROVIDER`, `BREVO_API_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME` | Transactional email through Brevo. The sender domain must be verified in Brevo. A blank key disables email. |
| `FIREBASE_SERVICE_ACCOUNT` | Push. The Firebase service-account JSON, base64-encoded (`base64 -w0 service-account.json`). `FIREBASE_SERVICE_ACCOUNT_FILE` (a path) also works on a machine that has the file. |
| `NEXT_PUBLIC_FIREBASE_*` | Push in the browser: the Firebase *web app* config plus its VAPID key (Firebase console → Project settings → Cloud Messaging → Web Push certificates). All five or none. |
| `WINK_GITHUB_REPO`, `GITHUB_TOKEN` | Where the APK releases live; the token raises GitHub's rate limit. |
| `NEXT_PUBLIC_SITE_URL` | Public URL for links and metadata. Default `https://wink.arkhins.com`. |
| `WINK_BASE_URL`, `WINK_UPDATE_URL` | App only: where it finds the server. |

### Deploying

1. Import the repository in Vercel; root directory is the repository root.
2. Add the environment variables above (the service account as base64:
   `base64 -w0 service-account.json`, the same value as in `.env`).
3. Run `npm run migrate` once against the production database.
4. Point `wink.arkhins.com` at Vercel (CNAME to `cname.vercel-dns.com`).
5. `npm run create-admin -- you@example.com` and accept the invite.

`public/.well-known/assetlinks.json` lists the SHA-256 of the release signing
certificate (and the debug one). If the keystore ever changes, update it or
invite links stop opening the app.

## Code layout

```
db/migrations/           schema, applied in order by scripts/migrate.mjs
scripts/                 migrate.mjs, create-admin.mjs
src/app/                 pages (App Router) and API routes
src/components/          client components: shell, inbox, composer, scanner…
src/lib/
  auth.ts                passwords (scrypt), sessions, invite/reset tokens
  hierarchy.ts           who may see / message / edit whom
  messages.ts            broadcasts, channels, private chats, inbox
  notify.ts              delivery: recipients → push → email
  push.ts, email.ts      Firebase Cloud Messaging, Brevo
  races.ts               weekends, sessions, next-race, schedule announcements
  files.ts, storage.ts   documents: signed URLs, S3 or local disk
  users.ts, roles.ts     the user shape and the role table
```
