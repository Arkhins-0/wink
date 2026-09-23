import "server-only";

import { bool, str } from "./api";
import type { WeekendInput } from "./races";
import { isTimeZone } from "./time";

/** The admin's weekend form, checked. */
export function weekendInput(b: Record<string, unknown>): WeekendInput | { error: string } {
  const name = str(b.name, 120);
  const timezone = str(b.timezone, 64) || "UTC";
  const startsOn = str(b.startsOn, 10);
  const endsOn = str(b.endsOn, 10);
  if (name.length < 2) return { error: "Give the weekend a name." };
  if (!isTimeZone(timezone)) return { error: "That time zone is not recognised." };
  const day = /^\d{4}-\d{2}-\d{2}$/;
  if (!day.test(startsOn) || !day.test(endsOn)) return { error: "Enter the first and last day." };
  if (endsOn < startsOn) return { error: "The last day is before the first." };
  return {
    name,
    venue: str(b.venue, 120),
    city: str(b.city, 80),
    country: str(b.country, 80),
    timezone,
    startsOn,
    endsOn,
    channelOpen: b.channelOpen === undefined ? true : bool(b.channelOpen),
  };
}
