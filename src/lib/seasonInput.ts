import "server-only";

import { str } from "./api";
import type { SeasonInput } from "./seasons";

/** The admin's season form, checked. */
export function seasonInput(b: Record<string, unknown>): SeasonInput | { error: string } {
  const name = str(b.name, 80);
  const startsOn = str(b.startsOn, 10);
  const endsOn = str(b.endsOn, 10) || null;
  const day = /^\d{4}-\d{2}-\d{2}$/;
  if (name.length < 2) return { error: "Name the season." };
  if (!day.test(startsOn)) return { error: "Choose the season's first day." };
  if (endsOn && (!day.test(endsOn) || endsOn < startsOn)) return { error: "The last day is before the first." };
  return { name, startsOn, endsOn };
}
