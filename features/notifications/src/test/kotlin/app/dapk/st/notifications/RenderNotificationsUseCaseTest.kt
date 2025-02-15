package app.dapk.st.notifications

import fake.*
import fixture.NotificationDiffFixtures.aNotificationDiff
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import test.expect

private val AN_UNREAD_NOTIFICATIONS = UnreadNotifications(emptyMap(), aNotificationDiff())

class RenderNotificationsUseCaseTest {

    private val fakeNotificationMessageRenderer = FakeNotificationMessageRenderer()
    private val fakeNotificationInviteRenderer = FakeNotificationInviteRenderer()
    private val fakeObserveUnreadNotificationsUseCase = FakeObserveUnreadNotificationsUseCase()
    private val fakeObserveInviteNotificationsUseCase = FakeObserveInviteNotificationsUseCase()
    private val fakeNotificationChannels = FakeNotificationChannels().also {
        it.instance.expect { it.initChannels() }
    }

    private val renderNotificationsUseCase = RenderNotificationsUseCase(
        fakeNotificationMessageRenderer.instance,
        fakeNotificationInviteRenderer.instance,
        fakeObserveUnreadNotificationsUseCase,
        fakeObserveInviteNotificationsUseCase,
        fakeNotificationChannels.instance,
    )

    @Test
    fun `given events, when listening for changes then initiates channels once`() = runTest {
        fakeNotificationMessageRenderer.instance.expect { it.render(any()) }
        fakeObserveUnreadNotificationsUseCase.given().emits(AN_UNREAD_NOTIFICATIONS)
        fakeObserveInviteNotificationsUseCase.given().emits()

        renderNotificationsUseCase.listenForNotificationChanges(TestScope(UnconfinedTestDispatcher()))

        fakeNotificationChannels.verifyInitiated()
    }

    @Test
    fun `given renderable unread events, when listening for changes, then renders change`() = runTest {
        fakeNotificationMessageRenderer.instance.expect { it.render(any()) }
        fakeObserveUnreadNotificationsUseCase.given().emits(AN_UNREAD_NOTIFICATIONS)
        fakeObserveInviteNotificationsUseCase.given().emits()

        renderNotificationsUseCase.listenForNotificationChanges(TestScope(UnconfinedTestDispatcher()))

        fakeNotificationMessageRenderer.verifyRenders(AN_UNREAD_NOTIFICATIONS)
    }
}
