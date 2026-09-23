package com.arkhins.wink.data

import kotlinx.serialization.Serializable

/*
 * What the server sends. Field names match the JSON of the website's API
 * (the TypeScript files under src/lib) exactly; unknown keys are ignored.
 */

@Serializable
data class PublicUser(
    val id: String,
    val email: String,
    val role: String,
    val roleLabel: String,
    val status: String,
    val statusLabel: String,
    val name: String? = null,
    val dob: String? = null,
    val phone: String? = null,
    val teamName: String? = null,
    val parentId: String? = null,
    val photoUrl: String? = null,
    val verifyCode: String,
    val profileComplete: Boolean = false,
) {
    val displayName: String get() = name ?: email
}

@Serializable
data class ParentInfo(val id: String, val name: String, val roleLabel: String)

@Serializable
data class Me(
    val user: PublicUser,
    val qrUrl: String,
    val parent: ParentInfo? = null,
    val canCreate: List<String> = emptyList(),
    val canPostChannel: Boolean = false,
    val canRelay: Boolean = false,
    val canBulkEmail: Boolean = false,
    val isAdmin: Boolean = false,
    val unread: Int = 0,
    val unreadChats: Int = 0,
    val unreadHome: Int = 0,
    val pushConfigured: Boolean = false,
)

@Serializable
data class LoginResponse(val token: String? = null, val user: PublicUser)

@Serializable
data class InviteInfo(val email: String, val role: String = "", val roleLabel: String = "")

@Serializable
data class ResetInfo(val email: String)

@Serializable
data class Sender(val id: String, val name: String, val role: String, val roleLabel: String, val photoUrl: String? = null)

@Serializable
data class FileInfo(val id: String, val name: String, val mime: String, val size: Long = 0)

@Serializable
data class Message(
    val id: String,
    val conversationId: String? = null,
    val kind: String = "broadcast",
    val weekendId: String? = null,
    val sender: Sender? = null,
    val body: String = "",
    val file: FileInfo? = null,
    val urgent: Boolean = false,
    val createdAt: String,
    val readAt: String? = null,
    val mine: Boolean = false,
    /** The message this one answers, as it read when fetched. */
    val replyTo: ReplyRef? = null,
    val editedAt: String? = null,
    val deleted: Boolean = false,
    /** Last edited, deleted, delivered or read; the phone asks for changes after the newest of these. */
    val changedAt: String? = null,
    /** Your own private message: "sent", "delivered" or "read" (one, two, three ticks). */
    val status: String? = null,
)

/** The Privacy Policy or the Terms, as /api/legal/<doc> gives them. */
@Serializable
data class LegalDoc(val title: String, val updated: String = "", val intro: List<LegalBlock> = emptyList(), val sections: List<LegalSection> = emptyList())

@Serializable
data class LegalSection(val title: String, val blocks: List<LegalBlock> = emptyList())

/** A paragraph (p) or a bulleted list (ul). */
@Serializable
data class LegalBlock(val p: String? = null, val ul: List<String>? = null)

@Serializable
data class ChangelogEntry(val version: String, val date: String = "", val changes: List<String> = emptyList())

@Serializable
data class ChangelogResponse(val releases: List<ChangelogEntry> = emptyList())

/** What sending to a chat answers: the new message, so the phone need not ask again. */
@Serializable
data class ChatSent(val id: String, val message: Message? = null)

@Serializable
data class ReplyRef(
    val id: String,
    val senderName: String = "",
    val mine: Boolean = false,
    val body: String = "",
    val fileName: String? = null,
    val fileMime: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class MessagesResponse(val messages: List<Message>)

@Serializable
data class UnseenResponse(val messages: List<Message>, val unread: Int, val unreadChats: Int = 0, val unreadHome: Int = 0, val now: String)

@Serializable
data class OtherUser(val id: String, val name: String, val role: String = "", val roleLabel: String = "", val photoUrl: String? = null, val status: String = "active")

@Serializable
data class Conversation(val id: String, val other: OtherUser, val iOpened: Boolean = false, val lastMessageAt: String? = null, val lastMessage: String? = null, val unread: Int = 0)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation>)

@Serializable
data class ConversationDetail(val id: String, val iOpened: Boolean = false, val other: OtherUser? = null, val messages: List<Message>, val liveIds: List<String>? = null)

@Serializable
data class ChannelResponse(val channelId: String, val open: Boolean, val canPost: Boolean, val messages: List<Message>)

@Serializable
data class RaceSession(val id: String, val weekendId: String = "", val name: String, val startsAt: String, val endsAt: String)

@Serializable
data class Weekend(
    val id: String,
    val name: String,
    val venue: String = "",
    val city: String = "",
    val country: String = "",
    val timezone: String = "UTC",
    val startsOn: String,
    val endsOn: String,
    val channelOpen: Boolean = true,
    val seasonId: String? = null,
    val seasonName: String? = null,
    val seasonArchived: Boolean = false,
    val sessions: List<RaceSession> = emptyList(),
) {
    val place: String get() = listOf(venue, city, country).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class WeekendsResponse(val weekends: List<Weekend>)

@Serializable
data class WeekendResponse(val weekend: Weekend, val channelId: String? = null, val canPost: Boolean = false)

@Serializable
data class NextRace(
    val state: String,
    val weekend: Weekend? = null,
    val session: RaceSession? = null,
    val later: List<RaceSession> = emptyList(),
    val now: String? = null,
)

@Serializable
data class UsersResponse(val users: List<PublicUser>)

@Serializable
data class UserResponse(val user: PublicUser, val qrUrl: String? = null, val canEdit: Boolean = false)

@Serializable
data class IdResponse(val id: String)

@Serializable
data class SentResponse(val id: String, val delivered: Int = 0)

@Serializable
data class UploadSlot(val id: String, val uploadUrl: String, val direct: Boolean, val maxProxyBytes: Long = 0)

@Serializable
data class FileMeta(val id: String, val name: String, val mime: String, val size: Long = 0, val downloadUrl: String, val viewUrl: String)

@Serializable
data class Verified(
    val id: String,
    val name: String? = null,
    val role: String,
    val roleLabel: String,
    val teamName: String? = null,
    val status: String,
    val statusLabel: String,
    val verifyCode: String,
    val photoUrl: String? = null,
    val profileComplete: Boolean = false,
)

@Serializable
data class Season(
    val id: String,
    val name: String,
    val startsOn: String,
    val endsOn: String? = null,
    val status: String = "active",
    val archivedAt: String? = null,
    val current: Boolean = false,
    val weekends: Int = 0,
)

@Serializable
data class SeasonsResponse(val seasons: List<Season>)

@Serializable
data class SeasonResponse(val season: Season)

@Serializable
data class ArchivedSender(val id: String, val name: String, val roleLabel: String = "")

@Serializable
data class ArchivedMessage(
    val id: String,
    val body: String = "",
    val urgent: Boolean = false,
    val createdAt: String,
    val sender: ArchivedSender? = null,
    val file: FileInfo? = null,
    val mine: Boolean = false,
)

@Serializable
data class ArchivedWeekend(
    val id: String,
    val name: String,
    val venue: String = "",
    val city: String = "",
    val country: String = "",
    val timezone: String = "UTC",
    val startsOn: String,
    val endsOn: String,
    val sessions: List<RaceSession> = emptyList(),
    val posts: List<ArchivedMessage> = emptyList(),
) {
    val place: String get() = listOf(venue, city, country).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class ArchivedChat(val other: OtherUser, val messages: List<ArchivedMessage>)

@Serializable
data class SeasonArchive(
    val season: Season,
    val weekends: List<ArchivedWeekend> = emptyList(),
    val announcements: List<ArchivedMessage> = emptyList(),
    val chats: List<ArchivedChat> = emptyList(),
)

@Serializable
data class Ok(val ok: Boolean = true)
