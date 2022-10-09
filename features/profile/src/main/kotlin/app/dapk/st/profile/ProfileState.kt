package app.dapk.st.profile

import app.dapk.st.core.Lce
import app.dapk.st.design.components.Route
import app.dapk.st.design.components.SpiderPage
import app.dapk.st.engine.Me
import app.dapk.st.engine.RoomInvite

data class ProfileScreenState(
    val page: SpiderPage<out Page>,
)

sealed interface Page {
    data class Profile(val content: Lce<Content>) : Page {
        data class Content(
            val me: Me,
            val invitationsCount: Int,
        )
    }

    data class Invitations(val content: Lce<List<RoomInvite>>) : Page

    object Routes {
        val profile = Route<Profile>("Profile")
        val invitation = Route<Invitations>("Invitations")
    }
}

sealed interface ProfileEvent {

}

