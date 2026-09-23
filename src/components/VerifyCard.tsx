import { Avatar } from "./Avatar";
import { ChatButton } from "./ChatButton";
import { StatusBadge } from "./StatusBadge";
import type { Status } from "@/lib/roles";

export type Verified = {
  id: string;
  name: string | null;
  roleLabel: string;
  teamName: string | null;
  status: Status;
  verifyCode: string;
  photoUrl: string | null;
  profileComplete: boolean;
};

/** What a scan shows: who, what role, and whether the account is in good standing. */
export function VerifyCard({ v, chat = true }: { v: Verified; chat?: boolean }) {
  const good = v.status === "active";
  return (
    <div className="space-y-3">
    <div className={`flex items-center gap-4 rounded-2xl border p-4 ${good ? "border-emerald-500/40" : "border-danger/50"}`}>
      <Avatar src={v.photoUrl} name={v.name ?? "?"} size={64} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-lg font-semibold">{v.name ?? "Profile not completed"}</p>
        <p className="text-sm text-snow-soft">
          {v.roleLabel}
          {v.teamName ? ` · ${v.teamName}` : ""}
        </p>
        <p className="font-mono text-xs tracking-widest text-snow-faint">{v.verifyCode}</p>
      </div>
      <StatusBadge status={v.status} />
    </div>
    {chat && good && <ChatButton userId={v.id} name={v.name ?? "this person"} />}
    </div>
  );
}
