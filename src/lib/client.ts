// Browser-side helpers. No "server-only", no Node built-ins.

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/** Call our API: JSON in, JSON out, errors as ApiError with the server's message. */
export async function api<T = unknown>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  const response = await fetch(path, {
    credentials: "same-origin",
    ...rest,
    headers: { ...(json !== undefined ? { "content-type": "application/json" } : {}), ...(headers ?? {}) },
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });
  const text = await response.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!response.ok) {
    const message = (data as { error?: string } | null)?.error ?? `Request failed (${response.status}).`;
    throw new ApiError(response.status, message);
  }
  return data as T;
}

/**
 * Upload a document: ask for a slot, PUT the bytes where told (straight to
 * storage, or through the server), then confirm. Returns the file id.
 */
export async function uploadFile(file: File, onProgress?: (fraction: number) => void): Promise<string> {
  const slot = await api<{ id: string; uploadUrl: string; direct: boolean; maxProxyBytes: number }>("/api/files", {
    method: "POST",
    json: { name: file.name, mime: file.type || "application/octet-stream", size: file.size },
  });
  const put = (url: string) =>
    new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("PUT", url);
      xhr.setRequestHeader("content-type", file.type || "application/octet-stream");
      xhr.upload.onprogress = (e) => e.lengthComputable && onProgress?.(e.loaded / e.total);
      xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status}).`)));
      xhr.onerror = () => reject(new Error("Upload failed."));
      xhr.send(file);
    });
  try {
    await put(slot.uploadUrl);
  } catch (error) {
    // The bucket refused the browser (CORS): small files can still go through the server.
    if (!slot.direct || file.size > slot.maxProxyBytes) throw error;
    await put(`/api/files/${slot.id}/content`);
  }
  await api(`/api/files/${slot.id}/ready`, { method: "POST" });
  return slot.id;
}

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/** A photo shrunk to fit 900px and re-encoded as JPEG, so uploads are small. */
export async function shrinkImage(file: File, max = 900): Promise<Blob> {
  const bitmap = await createImageBitmap(file).catch(() => null);
  if (!bitmap) return file;
  const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve) => canvas.toBlob((b) => resolve(b ?? file), "image/jpeg", 0.85));
}

export const timeAgo = (iso: string): string => {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60_000);
  if (m < 1) return "now";
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h`;
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });
};

/*
 * Stale refreshes. A list on screen reloads every few seconds; a vote or an event answer made meanwhile must not be
 * undone by a reload that set off before it, or that lands while it is still on its way. Whatever changes something
 * calls [startChange] and the function it returns when done; a reload takes [changeMark] as it sets off and drops
 * its answer if [isStale] says so.
 */
let changes = 0;
let pending = 0;
export const changeMark = () => changes;
export const isStale = (mark: number) => pending > 0 || mark !== changes;
export function startChange(): () => void {
  changes++;
  pending++;
  let done = false;
  return () => {
    if (done) return;
    done = true;
    pending--;
    changes++;
  };
}
