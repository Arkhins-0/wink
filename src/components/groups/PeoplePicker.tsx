"use client";

import { Avatar } from "../Avatar";
import { matches, type PickPerson } from "./people";

export type { PickPerson };

/** A list of people with a checkbox each. */
export function PeoplePicker({
  people,
  picked,
  filter,
  onChange,
  disabled,
}: {
  people: PickPerson[];
  picked: Set<string>;
  filter: string;
  onChange: (next: Set<string>) => void;
  disabled?: boolean;
}) {
  const shown = people.filter((p) => matches(p, filter));
  const toggle = (id: string) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onChange(next);
  };
  return (
    <div className="divide-y divide-night-line">
      {shown.map((p) => (
        <label key={p.id} className="row cursor-pointer rounded-none">
          <Avatar src={p.photoUrl} name={p.name} size={40} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{p.name}</span>
            <span className="block truncate text-xs text-snow-faint">
              {p.roleLabel}
              {p.teamName ? ` · ${p.teamName}` : ""}
            </span>
            {p.groupMode === "request" && <span className="block text-[11px] font-semibold text-gold">Higher up · gets a join request</span>}
          </span>
          <input
            type="checkbox"
            className="h-5 w-5 shrink-0 accent-[#FFD100]"
            checked={picked.has(p.id)}
            onChange={() => toggle(p.id)}
            disabled={disabled}
          />
        </label>
      ))}
      {shown.length === 0 && <p className="py-3 text-sm text-snow-faint">No one matches.</p>}
    </div>
  );
}
