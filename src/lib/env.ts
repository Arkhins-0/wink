import "server-only";

/**
 * The server's environment, read in one place. Every key here is set in the
 * root .env (see .env.example) or in the host's environment variables.
 * Nothing here is ever sent to the browser or compiled into the app.
 */

const read = (name: string, fallback = ""): string => (process.env[name] ?? fallback).trim();

export const env = {
  /** Postgres (Neon). The pooled URL is for many short connections, as on serverless. */
  databaseUrl: read("DATABASE_URL"),
  databaseUrlPooled: read("DATABASE_URL_POOLED"),

  /** S3-compatible object storage (Neon storage). */
  s3: {
    endpoint: read("AWS_ENDPOINT_URL_S3"),
    accessKeyId: read("AWS_ACCESS_KEY_ID"),
    secretAccessKey: read("AWS_SECRET_ACCESS_KEY"),
    region: read("AWS_REGION", "us-east-2"),
    bucket: read("S3_BUCKET"),
    /** The "folder" at the bucket's root that everything sits under. */
    prefix: read("S3_PREFIX", "wink").replace(/^\/+|\/+$/g, ""),
  },

  /** GitHub "owner/name" whose Releases carry the Android APKs. */
  githubRepo: read("WINK_GITHUB_REPO", "Arkhins-0/wink").replace(/^\/+|\/+$/g, ""),
  /** Optional token so release checks are not limited to 60 an hour. */
  githubToken: read("GITHUB_TOKEN"),
};

export const isDatabaseConfigured = (): boolean => Boolean(env.databaseUrlPooled || env.databaseUrl);

export const isStorageConfigured = (): boolean =>
  Boolean(env.s3.bucket && env.s3.accessKeyId && env.s3.secretAccessKey);
