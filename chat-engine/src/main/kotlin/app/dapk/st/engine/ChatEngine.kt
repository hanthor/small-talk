package app.dapk.st.engine

import app.dapk.st.matrix.common.EventId
import app.dapk.st.matrix.common.JsonString
import app.dapk.st.matrix.common.RoomId
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface ChatEngine {

    fun directory(): Flow<DirectoryState>

    fun invites(): Flow<InviteState>

    suspend fun messages(roomId: RoomId, disableReadReceipts: Boolean): Flow<MessengerState>

    suspend fun login(request: LoginRequest): LoginResult

    suspend fun me(forceRefresh: Boolean): Me

    suspend fun InputStream.importRoomKeys(password: String): Flow<ImportResult>

    suspend fun send(message: SendMessage, room: RoomOverview)

    suspend fun registerPushToken(token: String, gatewayUrl: String)

    suspend fun joinRoom(roomId: RoomId)

    suspend fun rejectJoinRoom(roomId: RoomId)

    fun mediaDecrypter(): MediaDecrypter

    fun pushHandler(): PushHandler
}

interface MediaDecrypter {

    fun decrypt(input: InputStream, k: String, iv: String): Collector

    fun interface Collector {
        fun collect(partial: (ByteArray) -> Unit)
    }

}

interface PushHandler {
    fun onNewToken(payload: JsonString)
    fun onMessageReceived(eventId: EventId?, roomId: RoomId?)
}
