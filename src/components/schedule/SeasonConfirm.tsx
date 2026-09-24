"use client";

import type { Season } from "@/lib/seasons";
import { ConfirmDialog } from "./ConfirmDialog";

export type SeasonAction = "current" | "archive" | "delete";

/**
 * A season is a big switch, so each change says what it means before it
 * happens — the same words as the app. `oldName` is the season that is
 * current now, which making another one current archives.
 */
export function SeasonConfirm({
  what,
  season: s,
  oldName,
  busy,
  onConfirm,
  onCancel,
}: {
  what: SeasonAction;
  season: Season;
  oldName?: string | null;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const title = what === "delete" ? `Delete ${s.name}?` : what === "current" ? `Make ${s.name} the current season?` : `Archive ${s.name}?`;
  const risk =
    what === "delete"
      ? "Everything in it — its race weekends, sessions, channel posts, announcements and the private chat messages sent in it — is removed from the database. There is no way to bring it back."
      : what === "current"
        ? `New race weekends, announcements and messages go into ${s.name} from now on.` +
          (oldName
            ? ` ${oldName} is archived: its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back.`
            : "")
        : "Its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back.";

  return (
    <ConfirmDialog
      title={title}
      lead={what === "delete" ? "This cannot be undone." : "Everyone is affected."}
      body={risk}
      note={what === "delete" ? undefined : "Private chats are not touched."}
      confirmLabel={what === "delete" ? "Delete for good" : what === "current" ? "Continue" : "Archive"}
      danger={what === "delete"}
      busy={busy}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
