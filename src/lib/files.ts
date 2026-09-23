import "server-only";

import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { env, isStorageConfigured } from "./env";
import { one, run } from "./db";
import { storage } from "./storage";

/*
 * Documents. The bytes go to storage; the row says what they are. On S3
 * the browser and the app talk to the bucket directly through short-lived
 * signed URLs, which keeps big PDFs clear of the server's request limit.
 * On local disk (development) everything streams through /api/files.
 */

export type FileRow = {
  id: string;
  key: string;
  name: string;
  mime: string;
  size: number;
  uploaded_by: string | null;
  ready: boolean;
  created_at: string;
};

export const MAX_FILE_BYTES = 50 * 1024 * 1024;
/** Files over this size cannot go through the server on Vercel; S3 direct upload only. */
export const MAX_PROXY_BYTES = 4 * 1024 * 1024;
const SIGNED_URL_SECONDS = 15 * 60;

const ALLOWED_MIME = new Set([
  "application/pdf",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "text/csv",
  "text/plain",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export const isAllowedMime = (mime: string): boolean => ALLOWED_MIME.has(mime);

export const isOfficeMime = (mime: string): boolean =>
  mime.startsWith("application/vnd.") || mime === "application/msword";

/** A safe name for a Content-Disposition header. */
export function safeName(name: string): string {
  const cleaned = name.replace(/[\r\n"\\/]/g, "_").trim() || "file";
  return cleaned.slice(0, 150);
}

let client: S3Client | undefined;
function s3(): S3Client {
  if (!client) {
    const { region, endpoint, accessKeyId, secretAccessKey } = env.s3;
    client = new S3Client({
      region,
      ...(endpoint ? { endpoint, forcePathStyle: true } : {}),
      credentials: { accessKeyId, secretAccessKey },
      // Otherwise the SDK signs a CRC32 of an empty body into every presigned
      // PUT, and the bucket rejects the real bytes a browser or the app sends.
      requestChecksumCalculation: "WHEN_REQUIRED",
      responseChecksumValidation: "WHEN_REQUIRED",
    });
  }
  return client;
}

const fullKey = (k: string) => (env.s3.prefix ? `${env.s3.prefix}/${k}` : k);

export async function fileById(id: string): Promise<FileRow | undefined> {
  return one<FileRow>("SELECT * FROM files WHERE id = $1", [id]);
}

/** Where a client should PUT the bytes. Direct to S3 when configured, through the server otherwise. */
export async function uploadTarget(file: FileRow): Promise<{ url: string; direct: boolean }> {
  if (!isStorageConfigured()) return { url: `/api/files/${file.id}/content`, direct: false };
  const url = await getSignedUrl(
    s3(),
    new PutObjectCommand({ Bucket: env.s3.bucket, Key: fullKey(file.key), ContentType: file.mime }),
    { expiresIn: SIGNED_URL_SECONDS },
  );
  return { url, direct: true };
}

/** A URL the bytes can be fetched from, for download or inline viewing. */
export async function downloadUrl(file: FileRow, inline: boolean): Promise<string> {
  if (!isStorageConfigured()) return `/api/files/${file.id}/content?inline=${inline ? 1 : 0}`;
  const disposition = `${inline ? "inline" : "attachment"}; filename="${safeName(file.name)}"`;
  return getSignedUrl(
    s3(),
    new GetObjectCommand({
      Bucket: env.s3.bucket,
      Key: fullKey(file.key),
      ResponseContentDisposition: disposition,
      ResponseContentType: file.mime,
    }),
    { expiresIn: SIGNED_URL_SECONDS },
  );
}

/** Confirm the bytes arrived (after a direct upload) and open the file for use. */
export async function markReady(file: FileRow): Promise<boolean> {
  const exists = await storage().exists(file.key);
  if (!exists) return false;
  await run("UPDATE files SET ready = true WHERE id = $1", [file.id]);
  return true;
}
