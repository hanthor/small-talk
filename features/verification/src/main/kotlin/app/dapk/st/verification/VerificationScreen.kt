package app.dapk.st.verification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun VerificationScreen(viewModel: VerificationViewModel) {


    Column {
        Text("Verification request")


        Row {
            Button(onClick = {
                viewModel.inSecureAccept()
            }) {
                Text("Yes".uppercase())
            }
        }


    }


}