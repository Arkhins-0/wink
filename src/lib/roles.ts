// Shared by server and client: no "server-only", no Node built-ins.

export const ROLES = [
  "admin",
  "coordinator",
  "race_official",
  "team_manager",
  "driver",
  "crew",
  "security_head",
  "security",
  "volunteer",
] as const;

export type Role = (typeof ROLES)[number];

export const STATUSES = ["pending", "active", "suspended", "dismissed", "banned"] as const;
export type Status = (typeof STATUSES)[number];

export const ROLE_LABEL: Record<Role, string> = {
  admin: "Admin",
  coordinator: "Coordinator",
  race_official: "Race official",
  team_manager: "Team manager",
  driver: "Driver",
  crew: "Crew",
  security_head: "Security head",
  security: "Security",
  volunteer: "Volunteer",
};

export const STATUS_LABEL: Record<Status, string> = {
  pending: "Pending",
  active: "Active",
  suspended: "Suspended",
  dismissed: "Dismissed",
  banned: "Banned",
};

/** Who may create whom. Anyone not listed creates nobody. */
export const CREATE_RULES: Partial<Record<Role, Role[]>> = {
  admin: ["admin", "coordinator"],
  coordinator: ["race_official", "team_manager", "security_head", "volunteer"],
  team_manager: ["driver", "crew"],
  security_head: ["security"],
};

export const canCreate = (creator: Role, role: Role): boolean => (CREATE_RULES[creator] ?? []).includes(role);

/** Roles that never get automatic email; their coordinator forwards by hand. */
export const NO_AUTO_EMAIL: Role[] = ["volunteer", "security"];

/** Roles allowed to post in a race-weekend channel. */
export const CHANNEL_POSTERS: Role[] = ["admin", "coordinator"];

export const isRole = (value: unknown): value is Role => ROLES.includes(value as Role);
export const isStatus = (value: unknown): value is Status => STATUSES.includes(value as Status);
