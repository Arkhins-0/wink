"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";
import { Icon } from "./Icon";

export type Preview = NonNullable<MessageOut["linkPreview"]>;

/** The first http(s) link in a text (the server reads it the same way, in linkPreview.ts). */
export function firstUrl(text: string): string | null {
  const m = /\bhttps?:\/\/[^\s<>"']+/i.exec(text);
  return m ? m[0].replace(/[.,;:!?)\]}'"]+$/, "") : null;
}

const host = (url: string) => {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
};

/**
 * While typing: the card for the first link, fetched once the typing pauses, as WhatsApp shows it above the box.
 * Its ✕ closes it for that link, and the message then goes as a plain link. Gives the card (or null) and the ✕.
 */
export function useLinkPreview(text: string, enabled: boolean) {
  const url = enabled ? firstUrl(text) : null;
  const [preview, setPreview] = useState<Preview | null>(null);
  const [loading, setLoading] = useState(false);
  const [closed, setClosed] = useState<string | null>(null);
  const asked = useRef<string | null>(null);

  useEffect(() => {
    if (!url || url === closed) {
      setPreview(null);
      setLoading(false);
      asked.current = null;
      return;
    }
    if (url === asked.current) return;
    setLoading(true);
    const t = window.setTimeout(async () => {
      asked.current = url;
      try {
        const r = await api<{ preview: Preview | null }>(`/api/link-preview?url=${encodeURIComponent(url)}`);
        if (asked.current === url) setPreview(r.preview);
      } catch {
        if (asked.current === url) setPreview(null);
      } finally {
        if (asked.current === url) setLoading(false);
      }
    }, 500);
    return () => window.clearTimeout(t);
  }, [url, closed]);

  const shown = url && url !== closed ? preview : null;
  return {
    preview: shown,
    loading: Boolean(url && url !== closed && loading && !shown),
    close: () => url && setClosed(url),
    /** What a send carries: the link whose card is open, else nothing. */
    linkUrl: shown ? shown.url : null,
    reset: () => {
      setClosed(null);
      setPreview(null);
      asked.current = null;
    },
  };
}

/** The card above the message box: picture, title, description and site, with the ✕ that sends it as a plain link. */
export function ComposerPreview({ preview, loading, onClose }: { preview: Preview | null; loading: boolean; onClose: () => void }) {
  if (!preview && !loading) return null;
  return (
    <div className="mx-1 mb-1 mt-0.5 flex min-h-[4.5rem] overflow-hidden rounded-2xl bg-night">
      <div className="flex w-20 shrink-0 items-center justify-center bg-night-line/40">
        {preview?.image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={preview.image} alt="" className="h-full w-full object-cover" />
        ) : (
          <Icon name="link" className="h-6 w-6 text-snow-faint" />
        )}
      </div>
      <div className="min-w-0 flex-1 px-3 py-2">
        {preview ? (
          <>
            <p className="truncate text-sm font-semibold text-snow">{preview.title || host(preview.url)}</p>
            {preview.description && <p className="line-clamp-2 text-xs text-snow-soft">{preview.description}</p>}
            <p className="truncate text-[11px] text-snow-faint">{host(preview.url)}</p>
          </>
        ) : (
          <div className="space-y-2 py-1">
            <div className="h-3 w-3/4 animate-pulse rounded bg-night-line" />
            <div className="h-3 w-1/2 animate-pulse rounded bg-night-line" />
          </div>
        )}
      </div>
      <button type="button" className="self-start p-2 text-snow-faint hover:text-snow" title="Send without the preview" aria-label="Remove preview" onClick={onClose}>
        <Icon name="close" className="h-4 w-4" />
      </button>
    </div>
  );
}

/** A sent message's card: the picture on top, then title, description and site; the whole card opens the link. */
export function LinkCard({ preview, onDark = true }: { preview: Preview; onDark?: boolean }) {
  return (
    <a
      href={preview.url}
      target="_blank"
      rel="noreferrer noopener"
      className={`mb-1 block overflow-hidden rounded-xl ${onDark ? "bg-night" : "bg-night/10"}`}
      onClick={(e) => e.stopPropagation()}
    >
      {preview.image && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={preview.image} alt="" loading="lazy" className="max-h-56 w-full object-cover" />
      )}
      <span className="block px-3 py-2">
        <span className={`block text-sm font-semibold ${onDark ? "text-snow" : "text-night"}`}>{preview.title || host(preview.url)}</span>
        {preview.description && <span className={`line-clamp-3 block text-xs ${onDark ? "text-snow-soft" : "text-night/70"}`}>{preview.description}</span>}
        <span className={`mt-1 flex items-center gap-1 text-xs ${onDark ? "text-snow-faint" : "text-night/60"}`}>
          <Icon name="link" className="h-3 w-3" /> {host(preview.url)}
        </span>
      </span>
    </a>
  );
}

/** The text under a card: the whole message when it is only the link is not said twice. */
export function textBesideCard(body: string, preview: Preview | null): string {
  if (!preview) return body;
  return body.trim() === firstUrl(body) ? "" : body;
}
