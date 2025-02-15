package app.dapk.st.notifications

import app.dapk.st.core.AppLogTag
import app.dapk.st.core.log
import app.dapk.st.matrix.common.CredentialsStore
import app.dapk.st.matrix.common.EventId
import app.dapk.st.matrix.common.RoomId
import app.dapk.st.matrix.sync.RoomStore
import app.dapk.st.matrix.sync.SyncService
import app.dapk.st.push.PushHandler
import app.dapk.st.push.PushTokenPayload
import app.dapk.st.work.WorkScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

private var previousJob: Job? = null

@OptIn(DelicateCoroutinesApi::class)
class MatrixPushHandler(
    private val workScheduler: WorkScheduler,
    private val credentialsStore: CredentialsStore,
    private val syncService: SyncService,
    private val roomStore: RoomStore,
) : PushHandler {

    override fun onNewToken(payload: PushTokenPayload) {
        log(AppLogTag.PUSH, "new push token received")
        workScheduler.schedule(
            WorkScheduler.WorkTask(
                type = "push_token",
                jobId = 2,
                jsonPayload = Json.encodeToString(PushTokenPayload.serializer(), payload)
            )
        )
    }

    override fun onMessageReceived(eventId: EventId?, roomId: RoomId?) {
        log(AppLogTag.PUSH, "push received")
        previousJob?.cancel()
        previousJob = GlobalScope.launch {
            when (credentialsStore.credentials()) {
                null -> log(AppLogTag.PUSH, "push ignored due to missing api credentials")
                else -> doSync(roomId, eventId)
            }
        }
    }

    private suspend fun doSync(roomId: RoomId?, eventId: EventId?) {
        when (roomId) {
            null -> {
                log(AppLogTag.PUSH, "empty push payload - keeping sync alive until unread changes")
                waitForUnreadChange(60_000) ?: log(AppLogTag.PUSH, "timed out waiting for sync")
            }

            else -> {
                log(AppLogTag.PUSH, "push with eventId payload - keeping sync alive until the event shows up in the sync response")
                waitForEvent(
                    timeout = 60_000,
                    eventId!!,
                ) ?: log(AppLogTag.PUSH, "timed out waiting for sync")
            }
        }
        log(AppLogTag.PUSH, "push sync finished")
    }

    private suspend fun waitForEvent(timeout: Long, eventId: EventId): EventId? {
        return withTimeoutOrNull(timeout) {
            combine(syncService.startSyncing().startInstantly(), syncService.observeEvent(eventId)) { _, event -> event }
                .firstOrNull {
                    it == eventId
                }
        }
    }

    private suspend fun waitForUnreadChange(timeout: Long): String? {
        return withTimeoutOrNull(timeout) {
            combine(syncService.startSyncing().startInstantly(), roomStore.observeUnread()) { _, unread -> unread }
                .first()
            "ignored"
        }
    }

    private fun Flow<Unit>.startInstantly() = this.onStart { emit(Unit) }
}
