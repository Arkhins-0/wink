package com.arkhins.wink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Ok
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.KeyValue
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** The account's details (read-only), changing the password, and a reset link for a forgotten one. */
@Composable
fun AccountDetailsScreen(vm: AppViewModel) {
    val me = vm.me ?: return
    val u = me.user
    var showPassword by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValue("Name", u.displayName)
                KeyValue("Email", u.email)
                KeyValue("Contact", u.phone ?: "—")
                KeyValue("Date of birth", u.dob ?: "—")
                KeyValue("Role", u.roleLabel + (u.teamName?.let { " · $it" } ?: ""))
                me.parent?.let { KeyValue("Reports to", "${it.name} · ${it.roleLabel}") }
                Spacer(Modifier.height(4.dp))
                Text("Profile details are locked. Your manager or an admin can change them.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
        }

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Password", style = MaterialTheme.typography.titleMedium, color = Snow)
                if (showPassword) ChangePasswordForm { showPassword = false }
                else GhostButton("Change password") { showPassword = true }
            }
        }

        ForgotPasswordPanel(u.email)
    }
}

@Composable
private fun ChangePasswordForm(onDone: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (done) {
            Text("Password changed. Other devices are signed out.", color = SnowSoft)
            GhostButton("Close", onClick = onDone)
            return@Column
        }
        ErrorText(error)
        Field(current, { current = it }, "Current password", password = true, enabled = !busy)
        Field(next, { next = it }, "New password", password = true, enabled = !busy)
        Field(again, { again = it }, "Repeat new password", password = true, enabled = !busy)
        GoldButton(if (busy) "Saving…" else "Change password", Modifier.fillMaxWidth(), enabled = !busy && next.length >= 8 && current.isNotBlank()) {
            if (next != again) {
                error = "The new passwords do not match."
                return@GoldButton
            }
            busy = true
            error = null
            scope.launch {
                try {
                    app.api.post("/api/auth/password", Ok.serializer()) {
                        put("current", current)
                        put("next", next)
                    }
                    done = true
                } catch (e: Exception) {
                    error = e.message ?: "Could not change."
                } finally {
                    busy = false
                }
            }
        }
        GhostButton("Cancel", enabled = !busy, onClick = onDone)
    }
}

/** Forgot the current password: the same reset link the sign-in page sends, to this account's email. */
@Composable
private fun ForgotPasswordPanel(email: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Forgot password", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(
                if (sent) "A link to choose a new password is on its way to $email. It works for 2 hours."
                else "Don't know your current password? Get a link at $email to choose a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowFaint,
            )
            ErrorText(error)
            if (!sent) GhostButton(if (busy) "Sending…" else "Email me a link", enabled = !busy) {
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.post("/api/auth/forgot", Ok.serializer()) { put("email", email) }
                        sent = true
                    } catch (e: Exception) {
                        error = e.message ?: "Could not send."
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
