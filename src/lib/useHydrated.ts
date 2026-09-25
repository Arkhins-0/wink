import { useSyncExternalStore } from "react";

const never = () => () => {};

/**
 * False while the server renders (and while the browser takes that render over), true after. Anything shown in the
 * reader's own time zone waits for it: the server runs in UTC and would print the wrong day and time, which the
 * browser could then not take over.
 */
export function useHydrated(): boolean {
  return useSyncExternalStore(never, () => true, () => false);
}
