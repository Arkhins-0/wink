// Picker rows, shared by server pages and client components.

/** Just what a picker row shows: no email, phone or date of birth. */
export type PickPerson = {
  id: string;
  name: string;
  roleLabel: string;
  teamName: string | null;
  photoUrl: string | null;
  /** For groups: "request" is someone higher up, who gets a join request instead. */
  groupMode?: "direct" | "request";
};

/** Trim a user from the API down to a picker row. */
export const pickPerson = (u: {
  id: string;
  name: string | null;
  email?: string;
  roleLabel: string;
  teamName?: string | null;
  photoUrl: string | null;
  groupMode?: "direct" | "request";
}): PickPerson => ({
  id: u.id,
  name: u.name || u.email || "Someone",
  roleLabel: u.roleLabel,
  teamName: u.teamName ?? null,
  photoUrl: u.photoUrl,
  groupMode: u.groupMode,
});

export const matches = (p: PickPerson, filter: string) =>
  !filter || `${p.name} ${p.teamName ?? ""} ${p.roleLabel}`.toLowerCase().includes(filter.trim().toLowerCase());
