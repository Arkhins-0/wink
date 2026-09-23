import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { announceScheduleChange, deleteSession, describeSession, upsertSession, weekendById } from "@/lib/races";
import { zonedToUtc } from "@/lib/time";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Admin: add or change a session. Times come as the track's wall clock
 * ("2026-10-03T14:00") and are stored as instants. Any change goes out to
 * everyone as urgent.
 */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const weekend = await weekendById(id);
  if (!weekend) return fail("No such race weekend.", 404);

  const b = await body(request);
  const sessionId = str(b.id, 64) || null;
  if (sessionId && !isUuid(sessionId)) return fail("No such session.", 404);
  const name = str(b.name, 80);
  const startsAt = zonedToUtc(str(b.startsAt, 32), weekend.timezone);
  const endsAt = zonedToUtc(str(b.endsAt, 32), weekend.timezone);
  if (name.length < 2) return fail("Name the session (e.g. Practice 1, Qualifying, Race).");
  if (!startsAt || !endsAt) return fail("Enter a start and end time.");
  if (endsAt <= startsAt) return fail("The session ends before it starts.");

  const before = sessionId ? weekend.sessions.find((s) => s.id === sessionId) : undefined;
  const saved = await upsertSession(id, sessionId, { name, startsAt, endsAt });
  const after = (await weekendById(id))!;
  const session = after.sessions.find((s) => s.id === saved)!;
  await audit(admin.id, id, sessionId ? "session.updated" : "session.created", { before, session });

  const changed = !before || before.startsAt !== session.startsAt || before.endsAt !== session.endsAt || before.name !== session.name;
  if (changed && !(b.quiet === true)) {
    await announceScheduleChange(admin, after, `${before ? "Changed" : "Added"} — ${describeSession(session, after.timezone)}`);
  }
  return json({ weekend: after, sessionId: saved });
});

export const DELETE = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  const sessionId = new URL(request.url).searchParams.get("session") ?? "";
  if (!isUuid(id) || !isUuid(sessionId)) return fail("No such session.", 404);
  const weekend = await weekendById(id);
  const session = weekend?.sessions.find((s) => s.id === sessionId);
  if (!weekend || !session) return fail("No such session.", 404);
  await deleteSession(id, sessionId);
  await audit(admin.id, id, "session.deleted", { session });
  const after = (await weekendById(id))!;
  await announceScheduleChange(admin, after, `Removed — ${session.name}`);
  return json({ weekend: after });
});
