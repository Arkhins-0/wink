/* eslint-disable @next/next/no-img-element */

export function Avatar({ src, name, size = 40 }: { src: string | null; name: string; size?: number }) {
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
  return src ? (
    <img
      src={src}
      alt=""
      width={size}
      height={size}
      className="shrink-0 rounded-full border border-night-line object-cover"
      style={{ width: size, height: size }}
    />
  ) : (
    <div
      className="flex shrink-0 items-center justify-center rounded-full border border-night-line bg-night text-snow-soft"
      style={{ width: size, height: size, fontSize: size / 2.6 }}
      aria-hidden
    >
      {initials || "?"}
    </div>
  );
}
