"use client";

/* eslint-disable @next/next/no-img-element */
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, shrinkImage } from "@/lib/client";

/** Photo, name, date of birth, contact number — once. */
export function OnboardingForm() {
  const router = useRouter();
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [dob, setDob] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!photo) return setPreview(null);
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!photo) return setError("Add a photo of yourself.");
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.set("name", name);
      form.set("dob", dob);
      form.set("phone", phone);
      form.set("photo", await shrinkImage(photo), "photo.jpg");
      await api("/api/me/profile", { method: "POST", body: form });
      router.replace("/home");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-sm text-snow-soft">These details go on your account and cannot be changed by you afterwards.</p>
      {error && <p className="error">{error}</p>}
      <label className="flex cursor-pointer items-center gap-4">
        {preview ? (
          <img src={preview} alt="" className="h-20 w-20 rounded-full border border-night-line object-cover" />
        ) : (
          <span className="flex h-20 w-20 items-center justify-center rounded-full border border-dashed border-snow/30 text-xs text-snow-faint">
            Photo
          </span>
        )}
        <span className="btn-ghost text-xs">{photo ? "Change photo" : "Add photo"}</span>
        <input type="file" accept="image/*" capture="user" className="hidden" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
      </label>
      <div>
        <label className="label" htmlFor="name">
          Full name
        </label>
        <input id="name" className="input" autoComplete="name" required minLength={2} value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div>
        <label className="label" htmlFor="dob">
          Date of birth
        </label>
        <input id="dob" className="input" type="date" required max={new Date().toISOString().slice(0, 10)} value={dob} onChange={(e) => setDob(e.target.value)} />
      </div>
      <div>
        <label className="label" htmlFor="phone">
          Contact number
        </label>
        <input id="phone" className="input" type="tel" autoComplete="tel" required value={phone} onChange={(e) => setPhone(e.target.value)} />
      </div>
      <button className="btn-gold w-full" disabled={busy}>
        {busy ? "Saving…" : "Save and continue"}
      </button>
    </form>
  );
}
