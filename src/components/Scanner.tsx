"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import { VerifyCard, type Verified } from "./VerifyCard";

/**
 * Point the camera at someone's QR, or type their code. The browser's own
 * BarcodeDetector is used where it exists; jsQR everywhere else.
 */
export function Scanner() {
  const video = useRef<HTMLVideoElement>(null);
  const [scanning, setScanning] = useState(false);
  const [code, setCode] = useState("");
  const [result, setResult] = useState<Verified | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const lookup = async (query: string) => {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await api<Verified>(`/api/verify?${query}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "No match.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!scanning) return;
    let stream: MediaStream | undefined;
    let stop = false;
    let raf = 0;

    (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
        const v = video.current!;
        v.srcObject = stream;
        await v.play();

        const Detector = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => { detect(v: HTMLVideoElement): Promise<{ rawValue: string }[]> } }).BarcodeDetector;
        const detector = Detector ? new Detector({ formats: ["qr_code"] }) : null;
        const jsQR = detector ? null : (await import("jsqr")).default;
        const canvas = document.createElement("canvas");

        const tick = async () => {
          if (stop) return;
          let value: string | null = null;
          if (detector) {
            const codes = await detector.detect(v).catch(() => []);
            value = codes[0]?.rawValue ?? null;
          } else if (jsQR && v.videoWidth) {
            canvas.width = v.videoWidth;
            canvas.height = v.videoHeight;
            const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
            ctx.drawImage(v, 0, 0);
            const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
            value = jsQR(img.data, img.width, img.height)?.data ?? null;
          }
          if (value) {
            const token = value.match(/\/v\/([A-Za-z0-9_-]+)/)?.[1];
            setScanning(false);
            await lookup(token ? `token=${encodeURIComponent(token)}` : `code=${encodeURIComponent(value)}`);
            return;
          }
          raf = requestAnimationFrame(() => setTimeout(tick, 150));
        };
        tick();
      } catch {
        setError("The camera could not be opened. Allow camera access, or type the code instead.");
        setScanning(false);
      }
    })();

    return () => {
      stop = true;
      cancelAnimationFrame(raf);
      stream?.getTracks().forEach((t) => t.stop());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scanning]);

  return (
    <div className="card space-y-3">
      {scanning ? (
        <div className="space-y-2">
          <video ref={video} className="aspect-square w-full max-w-xs rounded-xl bg-night object-cover" muted playsInline />
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setScanning(false)}>
            Stop camera
          </button>
        </div>
      ) : (
        <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setScanning(true)} disabled={busy}>
          Open camera
        </button>
      )}
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (code.trim()) lookup(`code=${encodeURIComponent(code)}`);
        }}
      >
        <input className="input font-mono uppercase tracking-widest" placeholder="XXXX-XXXX" maxLength={9} value={code} onChange={(e) => setCode(e.target.value)} />
        <button className="btn-ghost px-4 text-xs" disabled={busy || !code.trim()}>
          Check
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      {result && <VerifyCard v={result} />}
    </div>
  );
}
