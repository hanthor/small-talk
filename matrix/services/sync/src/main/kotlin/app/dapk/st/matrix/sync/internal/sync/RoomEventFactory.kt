package app.dapk.st.matrix.sync.internal.sync

import app.dapk.st.matrix.common.RoomId
import app.dapk.st.matrix.common.UserCredentials
import app.dapk.st.matrix.common.convertMxUrToUrl
import app.dapk.st.matrix.sync.MessageMeta
import app.dapk.st.matrix.sync.RoomEvent
import app.dapk.st.matrix.sync.RoomMembersService
import app.dapk.st.matrix.sync.find
import app.dapk.st.matrix.sync.internal.request.ApiTimelineEvent

internal class RoomEventFactory(
    private val roomMembersService: RoomMembersService
) {

    suspend fun ApiTimelineEvent.TimelineMessage.toTextMessage(
        roomId: RoomId,
        content: String = this.asTextContent().formattedBody?.stripTags() ?: this.asTextContent().body ?: "redacted",
        edited: Boolean = false,
        utcTimestamp: Long = this.utcTimestamp,
    ) = RoomEvent.Message(
        eventId = this.id,
        content = content,
        author = roomMembersService.find(roomId, this.senderId)!!,
        utcTimestamp = utcTimestamp,
        meta = MessageMeta.FromServer,
        edited = edited,
    )

    suspend fun ApiTimelineEvent.TimelineMessage.toImageMessage(
        userCredentials: UserCredentials,
        roomId: RoomId,
        edited: Boolean = false,
        utcTimestamp: Long = this.utcTimestamp,
        imageMeta: RoomEvent.Image.ImageMeta = this.readImageMeta(userCredentials)
    ) = RoomEvent.Image(
        eventId = this.id,
        imageMeta = imageMeta,
        author = roomMembersService.find(roomId, this.senderId)!!,
        utcTimestamp = utcTimestamp,
        meta = MessageMeta.FromServer,
        edited = edited,
    )

    private fun ApiTimelineEvent.TimelineMessage.readImageMeta(userCredentials: UserCredentials): RoomEvent.Image.ImageMeta {
        val content = this.content as ApiTimelineEvent.TimelineMessage.Content.Image
        return RoomEvent.Image.ImageMeta(
            content.info?.width,
            content.info?.height,
            content.file?.url?.convertMxUrToUrl(userCredentials.homeServer) ?: content.url!!.convertMxUrToUrl(userCredentials.homeServer),
            keys = content.file?.let { RoomEvent.Image.ImageMeta.Keys(it.key.k, it.iv, it.v, it.hashes) }
        )
    }
}


private fun String.indexOfOrNull(string: String) = this.indexOf(string).takeIf { it != -1 }

fun String.stripTags() = this
    .run {
        this.indexOfOrNull("</mx-reply>")?.let {
            this.substring(it + "</mx-reply>".length)
        } ?: this
    }
    .trim()
    .replaceLinks()
    .removeTag("p")
    .removeTag("em")
    .removeTag("strong")
    .removeTag("code")
    .removeTag("pre")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("<br />", "\n")
    .replace("<br/>", "\n")

private fun String.removeTag(name: String) = this.replace("<$name>", "").replace("/$name>", "")

private fun String.replaceLinks(): String {
    return this.indexOfOrNull("<a href=")?.let { start ->
        val openTagClose = indexOfOrNull("\">")!!
        val end = indexOfOrNull("</a>")!!
        val content = this.substring(openTagClose + "\">".length, end)
        this.replaceRange(start, end + "</a>".length, content)
    } ?: this
}

private fun ApiTimelineEvent.TimelineMessage.asTextContent() = this.content as ApiTimelineEvent.TimelineMessage.Content.Text
