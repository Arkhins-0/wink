import "server-only";

import fs from "node:fs";
import path from "node:path";
import {
  DeleteObjectCommand,
  DeleteObjectsCommand,
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { env, isStorageConfigured } from "./env";

/**
 * Where files live: the S3-compatible bucket from .env when one is
 * configured, the local `data/` directory otherwise (development without
 * credentials). Same operations either way, keyed by a relative path such
 * as `uploads/<id>/file.png`. Objects sit under one prefix (`S3_PREFIX`,
 * default `wink`).
 */

export type Stored = { body: Buffer; contentType: string };

export type Storage = {
  put(key: string, body: Buffer, contentType: string): Promise<void>;
  get(key: string): Promise<Stored | null>;
  exists(key: string): Promise<boolean>;
  remove(key: string): Promise<void>;
  removePrefix(prefix: string): Promise<void>;
  list(prefix: string): Promise<string[]>;
  describe(): string;
};

/* ───────────────────────────── Local disk ────────────────────────── */

const DATA_DIR = path.resolve(process.cwd(), process.env.DATA_DIR || "./data");

function local(): Storage {
  const file = (key: string) => path.join(DATA_DIR, ...key.split("/"));
  const meta = (key: string) => `${file(key)}.type`;

  return {
    async put(key, body, contentType) {
      fs.mkdirSync(path.dirname(file(key)), { recursive: true });
      fs.writeFileSync(file(key), body);
      fs.writeFileSync(meta(key), contentType);
    },
    async get(key) {
      if (!fs.existsSync(file(key))) return null;
      const contentType = fs.existsSync(meta(key)) ? fs.readFileSync(meta(key), "utf8") : "application/octet-stream";
      return { body: fs.readFileSync(file(key)), contentType };
    },
    async exists(key) {
      return fs.existsSync(file(key));
    },
    async remove(key) {
      fs.rmSync(file(key), { force: true });
      fs.rmSync(meta(key), { force: true });
    },
    async removePrefix(prefix) {
      fs.rmSync(file(prefix), { recursive: true, force: true });
    },
    async list(prefix) {
      const root = file(prefix);
      if (!fs.existsSync(root)) return [];
      const out: string[] = [];
      const walk = (dir: string) => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) walk(full);
          else if (!entry.name.endsWith(".type")) out.push(path.relative(DATA_DIR, full).split(path.sep).join("/"));
        }
      };
      walk(root);
      return out;
    },
    describe: () => `local disk (${DATA_DIR})`,
  };
}

/* ───────────────────────────── S3 ────────────────────────────────── */

function s3(): Storage {
  const { bucket, region, prefix, endpoint, accessKeyId, secretAccessKey } = env.s3;

  const client = new S3Client({
    region,
    ...(endpoint ? { endpoint, forcePathStyle: true } : {}),
    credentials: { accessKeyId, secretAccessKey },
  });

  const full = (k: string) => (prefix ? `${prefix}/${k}` : k);
  const strip = (k: string) => (prefix && k.startsWith(`${prefix}/`) ? k.slice(prefix.length + 1) : k);
  const missing = (error: unknown) => {
    const name = (error as { name?: string }).name;
    return name === "NoSuchKey" || name === "NotFound";
  };

  return {
    async put(k, body, contentType) {
      await client.send(new PutObjectCommand({ Bucket: bucket, Key: full(k), Body: body, ContentType: contentType }));
    },
    async get(k) {
      try {
        const out = await client.send(new GetObjectCommand({ Bucket: bucket, Key: full(k) }));
        const bytes = await out.Body?.transformToByteArray();
        if (!bytes) return null;
        return { body: Buffer.from(bytes), contentType: out.ContentType || "application/octet-stream" };
      } catch (error) {
        if (missing(error)) return null;
        throw error;
      }
    },
    async exists(k) {
      try {
        await client.send(new HeadObjectCommand({ Bucket: bucket, Key: full(k) }));
        return true;
      } catch (error) {
        if (missing(error)) return false;
        throw error;
      }
    },
    async remove(k) {
      await client.send(new DeleteObjectCommand({ Bucket: bucket, Key: full(k) }));
    },
    async removePrefix(p) {
      let token: string | undefined;
      do {
        const page = await client.send(
          new ListObjectsV2Command({ Bucket: bucket, Prefix: full(p), ContinuationToken: token }),
        );
        const keys = (page.Contents ?? []).map((o) => ({ Key: o.Key! }));
        if (keys.length > 0) {
          await client.send(new DeleteObjectsCommand({ Bucket: bucket, Delete: { Objects: keys, Quiet: true } }));
        }
        token = page.IsTruncated ? page.NextContinuationToken : undefined;
      } while (token);
    },
    async list(p) {
      const keys: string[] = [];
      let token: string | undefined;
      do {
        const page = await client.send(
          new ListObjectsV2Command({ Bucket: bucket, Prefix: full(p), ContinuationToken: token }),
        );
        for (const o of page.Contents ?? []) if (o.Key) keys.push(strip(o.Key));
        token = page.IsTruncated ? page.NextContinuationToken : undefined;
      } while (token);
      return keys;
    },
    describe: () => `s3://${bucket}/${prefix} (${region}${endpoint ? `, ${endpoint}` : ""})`,
  };
}

/* ───────────────────────────── Pick one ──────────────────────────── */

declare global {
  // eslint-disable-next-line no-var
  var __winkStorage: Storage | undefined;
}

export function storage(): Storage {
  if (!globalThis.__winkStorage) globalThis.__winkStorage = isStorageConfigured() ? s3() : local();
  return globalThis.__winkStorage;
}
