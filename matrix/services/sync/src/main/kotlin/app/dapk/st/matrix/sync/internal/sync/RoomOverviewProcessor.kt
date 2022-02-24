package app.dapk.st.matrix.sync.internal.sync

import app.dapk.st.matrix.common.*
import app.dapk.st.matrix.sync.LastMessage
import app.dapk.st.matrix.sync.RoomMembersService
import app.dapk.st.matrix.sync.RoomOverview
import app.dapk.st.matrix.sync.find
import app.dapk.st.matrix.sync.internal.request.ApiAccountEvent
import app.dapk.st.matrix.sync.internal.request.ApiTimelineEvent

internal class RoomOverviewProcessor(
    private val roomMembersService: RoomMembersService,
) {

    suspend fun process(roomToProcess: RoomToProcess, previousState: RoomOverview?, lastMessage: LastMessage?): RoomOverview {
        val combinedEvents = roomToProcess.apiSyncRoom.state.stateEvents + roomToProcess.apiSyncRoom.timeline.apiTimelineEvents
        val isEncrypted = combinedEvents.any { it is ApiTimelineEvent.Encryption }

        val readMarker = roomToProcess.apiSyncRoom.accountData?.events?.filterIsInstance<ApiAccountEvent.FullyRead>()?.firstOrNull()?.content?.eventId
        return when (previousState) {
            null -> combinedEvents.filterIsInstance<ApiTimelineEvent.RoomCreate>().first().let { roomCreate ->
                val roomName = roomDisplayName(combinedEvents)
                val isGroup = roomToProcess.directMessage == null
                RoomOverview(
                    roomName = roomName ?: roomToProcess.directMessage?.let {
                        roomMembersService.find(roomToProcess.roomId, it)?.let { it.displayName ?: it.id.value }
                    },
                    roomCreationUtc = roomCreate.utcTimestamp,
                    lastMessage = lastMessage,
                    roomId = roomToProcess.roomId,
                    isGroup = isGroup,
                    roomAvatarUrl = roomAvatar(
                        roomToProcess.roomId,
                        roomMembersService,
                        roomToProcess.directMessage,
                        combinedEvents,
                        roomToProcess.userCredentials.homeServer
                    ),
                    readMarker = readMarker,
                    isEncrypted = isEncrypted,
                )
            }
            else -> {
                previousState.copy(
                    roomName = previousState.roomName ?: roomDisplayName(combinedEvents),
                    lastMessage = lastMessage ?: previousState.lastMessage,
                    roomAvatarUrl = previousState.roomAvatarUrl ?: roomAvatar(
                        roomToProcess.roomId,
                        roomMembersService,
                        roomToProcess.directMessage,
                        combinedEvents,
                        roomToProcess.userCredentials.homeServer,
                    ),
                    readMarker = readMarker ?: previousState.readMarker,
                    isEncrypted = isEncrypted || previousState.isEncrypted
                )
            }
        }
    }

    private fun roomDisplayName(combinedEvents: List<ApiTimelineEvent>): String? {
        val roomName = combinedEvents.filterIsInstance<ApiTimelineEvent.RoomName>().lastOrNull()
        return roomName?.content?.name
    }

    private suspend fun roomAvatar(
        roomId: RoomId,
        membersService: RoomMembersService,
        dmUser: UserId?,
        combinedEvents: List<ApiTimelineEvent>,
        homeServerUrl: HomeServerUrl
    ): AvatarUrl? {
        return when (dmUser) {
            null -> {
                val filterIsInstance = combinedEvents.filterIsInstance<ApiTimelineEvent.RoomAvatar>()
                filterIsInstance.lastOrNull()?.content?.url?.convertMxUrToUrl(homeServerUrl)?.let { AvatarUrl(it) }
            }
            else -> membersService.find(roomId, dmUser)?.avatarUrl
        }
    }
}