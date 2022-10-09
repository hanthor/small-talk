package app.dapk.st.engine

import app.dapk.st.matrix.common.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

typealias DirectoryState = List<DirectoryItem>
typealias OverviewState = List<RoomOverview>
typealias InviteState = List<RoomInvite>

data class DirectoryItem(
    val overview: RoomOverview,
    val unreadCount: UnreadCount,
    val typing: Typing?
)

data class RoomOverview(
    val roomId: RoomId,
    val roomCreationUtc: Long,
    val roomName: String?,
    val roomAvatarUrl: AvatarUrl?,
    val lastMessage: LastMessage?,
    val isGroup: Boolean,
    val readMarker: EventId?,
    val isEncrypted: Boolean,
) {

    data class LastMessage(
        val content: String,
        val utcTimestamp: Long,
        val author: RoomMember,
    )

}

data class RoomInvite(
    val from: RoomMember,
    val roomId: RoomId,
    val inviteMeta: InviteMeta,
) {
    sealed class InviteMeta {
        object DirectMessage : InviteMeta()
        data class Room(val roomName: String? = null) : InviteMeta()
    }

}

@JvmInline
value class UnreadCount(val value: Int)

data class Typing(val roomId: RoomId, val members: List<RoomMember>)

data class LoginRequest(val userName: String, val password: String, val serverUrl: String?)

sealed interface LoginResult {
    data class Success(val userCredentials: UserCredentials) : LoginResult
    object MissingWellKnown : LoginResult
    data class Error(val cause: Throwable) : LoginResult
}

data class Me(
    val userId: UserId,
    val displayName: String?,
    val avatarUrl: AvatarUrl?,
    val homeServerUrl: HomeServerUrl,
)

sealed interface ImportResult {
    data class Success(val roomIds: Set<RoomId>, val totalImportedKeysCount: Long) : ImportResult
    data class Error(val cause: Type) : ImportResult {

        sealed interface Type {
            data class Unknown(val cause: Throwable) : Type
            object NoKeysFound : Type
            object UnexpectedDecryptionOutput : Type
            object UnableToOpenFile : Type
            object InvalidFile : Type
        }

    }

    data class Update(val importedKeysCount: Long) : ImportResult
}

data class MessengerState(
    val self: UserId,
    val roomState: RoomState,
    val typing: Typing?
)

data class RoomState(
    val roomOverview: RoomOverview,
    val events: List<RoomEvent>,
)

internal val DEFAULT_ZONE = ZoneId.systemDefault()
internal val MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

sealed class RoomEvent {

    abstract val eventId: EventId
    abstract val utcTimestamp: Long
    abstract val author: RoomMember
    abstract val meta: MessageMeta

    data class Message(
        override val eventId: EventId,
        override val utcTimestamp: Long,
        val content: String,
        override val author: RoomMember,
        override val meta: MessageMeta,
        val edited: Boolean = false,
        val redacted: Boolean = false,
    ) : RoomEvent() {

        val time: String by lazy(mode = LazyThreadSafetyMode.NONE) {
            val instant = Instant.ofEpochMilli(utcTimestamp)
            ZonedDateTime.ofInstant(instant, DEFAULT_ZONE).toLocalTime().format(MESSAGE_TIME_FORMAT)
        }
    }

    data class Reply(
        val message: RoomEvent,
        val replyingTo: RoomEvent,
    ) : RoomEvent() {

        override val eventId: EventId = message.eventId
        override val utcTimestamp: Long = message.utcTimestamp
        override val author: RoomMember = message.author
        override val meta: MessageMeta = message.meta

        val replyingToSelf = replyingTo.author == message.author

        val time: String by lazy(mode = LazyThreadSafetyMode.NONE) {
            val instant = Instant.ofEpochMilli(utcTimestamp)
            ZonedDateTime.ofInstant(instant, DEFAULT_ZONE).toLocalTime().format(MESSAGE_TIME_FORMAT)
        }
    }

    data class Image(
        override val eventId: EventId,
        override val utcTimestamp: Long,
        val imageMeta: ImageMeta,
        override val author: RoomMember,
        override val meta: MessageMeta,
        val edited: Boolean = false,
    ) : RoomEvent() {

        val time: String by lazy(mode = LazyThreadSafetyMode.NONE) {
            val instant = Instant.ofEpochMilli(utcTimestamp)
            ZonedDateTime.ofInstant(instant, DEFAULT_ZONE).toLocalTime().format(MESSAGE_TIME_FORMAT)
        }

        data class ImageMeta(
            val width: Int?,
            val height: Int?,
            val url: String,
            val keys: Keys?,
        ) {

            data class Keys(
                val k: String,
                val iv: String,
                val v: String,
                val hashes: Map<String, String>,
            )

        }
    }

}

sealed class MessageMeta {

    object FromServer : MessageMeta()

    data class LocalEcho(
        val echoId: String,
        val state: State
    ) : MessageMeta() {

        sealed class State {
            object Sending : State()

            object Sent : State()

            data class Error(
                val message: String,
                val type: Type,
            ) : State() {

                enum class Type {
                    UNKNOWN
                }
            }
        }
    }
}