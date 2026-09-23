import { STATUS_LABEL, type Status } from "@/lib/roles";

const tone: Record<Status, string> = {
  active: "border-emerald-500/40 bg-emerald-500/10 text-emerald-300",
  pending: "border-snow/20 bg-snow/5 text-snow-soft",
  suspended: "border-amber-500/40 bg-amber-500/10 text-amber-300",
  dismissed: "border-snow/20 bg-snow/5 text-snow-faint",
  banned: "border-danger/40 bg-danger/10 text-danger",
};

export function StatusBadge({ status }: { status: Status }) {
  return <span className={`chip ${tone[status]}`}>{STATUS_LABEL[status]}</span>;
}
