package app.dapk.st.messenger

import androidx.lifecycle.viewModelScope
import app.dapk.st.core.Lce
import app.dapk.st.core.extensions.takeIfContent
import app.dapk.st.domain.application.message.MessageOptionsStore
import app.dapk.st.engine.ChatEngine
import app.dapk.st.engine.RoomEvent
import app.dapk.st.engine.SendMessage
import app.dapk.st.matrix.common.RoomId
import app.dapk.st.navigator.MessageAttachment
import app.dapk.st.viewmodel.DapkViewModel
import app.dapk.st.viewmodel.MutableStateFactory
import app.dapk.st.viewmodel.defaultStateFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class MessengerViewModel(
    private val chatEngine: ChatEngine,
    private val messageOptionsStore: MessageOptionsStore,
    factory: MutableStateFactory<MessengerScreenState> = defaultStateFactory(),
) : DapkViewModel<MessengerScreenState, MessengerEvent>(
    initialState = MessengerScreenState(
        roomId = null,
        roomState = Lce.Loading(),
        composerState = ComposerState.Text(value = "", reply = null)
    ),
    factory = factory,
) {

    private var syncJob: Job? = null

    fun post(action: MessengerAction) {
        when (action) {
            is MessengerAction.OnMessengerVisible -> start(action)
            MessengerAction.OnMessengerGone -> syncJob?.cancel()
            is MessengerAction.ComposerTextUpdate -> updateState { copy(composerState = ComposerState.Text(action.newValue, composerState.reply)) }
            MessengerAction.ComposerSendText -> sendMessage()
            MessengerAction.ComposerClear -> resetComposer()
            is MessengerAction.ComposerImageUpdate -> updateState {
                copy(
                    composerState = ComposerState.Attachments(
                        listOf(action.newValue),
                        composerState.reply
                    )
                )
            }

            is MessengerAction.ComposerEnterReplyMode -> updateState {
                copy(
                    composerState = when (composerState) {
                        is ComposerState.Attachments -> composerState.copy(reply = action.replyingTo)
                        is ComposerState.Text -> composerState.copy(reply = action.replyingTo)
                    }
                )
            }

            MessengerAction.ComposerExitReplyMode -> updateState {
                copy(
                    composerState = when (composerState) {
                        is ComposerState.Attachments -> composerState.copy(reply = null)
                        is ComposerState.Text -> composerState.copy(reply = null)
                    }
                )
            }
        }
    }

    private fun start(action: MessengerAction.OnMessengerVisible) {
        updateState { copy(roomId = action.roomId, composerState = action.attachments?.let { ComposerState.Attachments(it, null) } ?: composerState) }
        viewModelScope.launch {
            syncJob = chatEngine.messages(action.roomId, disableReadReceipts = messageOptionsStore.isReadReceiptsDisabled())
                .onEach { updateState { copy(roomState = Lce.Content(it)) } }
                .launchIn(this)
        }
    }


    private fun sendMessage() {
        when (val composerState = state.composerState) {
            is ComposerState.Text -> {
                val copy = composerState.copy()
                updateState { copy(composerState = composerState.copy(value = "", reply = null)) }

                state.roomState.takeIfContent()?.let { content ->
                    val roomState = content.roomState
                    viewModelScope.launch {
                        chatEngine.send(
                            message = SendMessage.TextMessage(
                                content = copy.value,
                                reply = copy.reply?.let {
                                    SendMessage.TextMessage.Reply(
                                        author = it.author,
                                        originalMessage = when (it) {
                                            is RoomEvent.Image -> TODO()
                                            is RoomEvent.Reply -> TODO()
                                            is RoomEvent.Message -> it.content
                                        },
                                        eventId = it.eventId,
                                        timestampUtc = it.utcTimestamp,
                                    )
                                }
                            ),
                            room = roomState.roomOverview,
                        )
                    }
                }
            }

            is ComposerState.Attachments -> {
                val copy = composerState.copy()
                resetComposer()

                state.roomState.takeIfContent()?.let { content ->
                    val roomState = content.roomState
                    viewModelScope.launch {
                        chatEngine.send(SendMessage.ImageMessage(uri = copy.values.first().uri.value), roomState.roomOverview)
                    }
                }

            }
        }
    }

    private fun resetComposer() {
        updateState { copy(composerState = ComposerState.Text("", reply = null)) }
    }

    fun startAttachment() {
        viewModelScope.launch {
            _events.emit(MessengerEvent.SelectImageAttachment)
        }
    }

}

sealed interface MessengerAction {
    data class ComposerTextUpdate(val newValue: String) : MessengerAction
    data class ComposerEnterReplyMode(val replyingTo: RoomEvent) : MessengerAction
    object ComposerExitReplyMode : MessengerAction
    data class ComposerImageUpdate(val newValue: MessageAttachment) : MessengerAction
    object ComposerSendText : MessengerAction
    object ComposerClear : MessengerAction
    data class OnMessengerVisible(val roomId: RoomId, val attachments: List<MessageAttachment>?) : MessengerAction
    object OnMessengerGone : MessengerAction
}