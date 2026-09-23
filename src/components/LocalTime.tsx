"use client";

import { useEffect, useState } from "react";

/** A time in the reader's own zone, rendered after mount so the server never guesses a zone. */
export function LocalTime({ iso, mode = "datetime" }: { iso: string; mode?: "datetime" | "time" | "date" }) {
  const [text, setText] = useState("");
  useEffect(() => {
    const d = new Date(iso);
    setText(
      mode === "time"
        ? d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit", hour12: true })
        : mode === "date"
          ? d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })
          : d.toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit", hour12: true }),
    );
  }, [iso, mode]);
  return <time dateTime={iso}>{text}</time>;
}
