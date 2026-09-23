import { Icon } from "@/components/Icon";

export const metadata = { title: "Chats" };

/** On a laptop the list is beside this; on a phone the list is the page. */
export default function Chats() {
  return (
    <div className="card hidden h-full min-h-0 flex-1 flex-col items-center justify-center text-center lg:flex">
      <Icon name="chat" className="h-10 w-10 text-snow-faint" />
      <p className="mt-3 text-sm text-snow-soft">Pick a chat, or start one with the pencil.</p>
    </div>
  );
}
