package com.arkhins.wink.ui.screens

import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Gold
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkhins.wink.Config
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.ApiException
import com.arkhins.wink.R
import com.arkhins.wink.data.InviteInfo
import com.arkhins.wink.data.LoginResponse
import com.arkhins.wink.data.Ok
import com.arkhins.wink.data.ResetInfo
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** The frame for every signed-out screen: the mark, the name, one panel. */
@Composable
fun AuthFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ctr_logo), contentDescription = null, modifier = Modifier.width(56.dp))
            Spacer(Modifier.width(12.dp))
            Text(Config.APP_NAME, style = MaterialTheme.typography.headlineMedium, color = Snow)
        }
        Spacer(Modifier.height(28.dp))
        Panel {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Snow)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: (String) -> Unit, onForgot: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AuthFrame("Sign in") {
        ErrorText(error)
        if (error != null) Spacer(Modifier.height(10.dp))
        Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
        Spacer(Modifier.height(10.dp))
        Field(password, { password = it }, "Password", password = true, enabled = !busy)
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "Signing in…" else "Sign in", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
            busy = true
            error = null
            scope.launch {
                try {
                    val r = app.api.login(email.trim(), password)
                    onSignedIn(r.token ?: throw IllegalStateException("No session returned."))
                } catch (e: Exception) {
                    // A banned account: nothing it left on this phone stays.
                    if ((e as? ApiException)?.reason == "banned") {
                        app.chatCache.wipe()
                        app.chatMedia.wipe()
                    }
                    error = e.message ?: "Could not sign in."
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Forgot password", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { onForgot() })
    }
}

@Composable
fun ForgotScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AuthFrame("Forgot password") {
        if (sent) {
            Text("If that email has an account, a link to choose a new password is on its way. It works for 2 hours.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            GhostButton("Back to sign in", Modifier.fillMaxWidth(), onClick = onBack)
        } else {
            Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
            Spacer(Modifier.height(16.dp))
            GoldButton(if (busy) "Sending…" else "Email me a link", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank()) {
                busy = true
                scope.launch {
                    runCatching { app.api.post("/api/auth/forgot", Ok.serializer()) { put("email", email.trim()) } }
                    sent = true
                    busy = false
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Back to sign in", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { onBack() })
        }
    }
}

/** The invite link (choose a first password) and the reset link (choose a new one). */
@Composable
fun SetPasswordScreen(kind: String, token: String, onSignedIn: (String) -> Unit, onDone: () -> Unit, onLegal: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val invite = kind == "invite"
    var email by remember { mutableStateOf<String?>(null) }
    var roleLabel by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        try {
            if (invite) {
                val info = app.api.get("/api/auth/invite/$token", InviteInfo.serializer())
                email = info.email
                roleLabel = info.roleLabel
            } else {
                email = app.api.get("/api/auth/reset/$token", ResetInfo.serializer()).email
            }
        } catch (e: Exception) {
            loadError = e.message ?: "This link is no longer valid."
        }
    }

    AuthFrame(if (invite) "Set up your account" else "Choose a new password") {
        when {
            loadError != null -> {
                ErrorText(loadError)
                Spacer(Modifier.height(12.dp))
                GhostButton("Back", Modifier.fillMaxWidth(), onClick = onDone)
            }
            done -> {
                Text("Your password is changed. Sign in with it.", color = SnowSoft)
                Spacer(Modifier.height(12.dp))
                GoldButton("Sign in", Modifier.fillMaxWidth(), onClick = onDone)
            }
            email == null -> Loading()
            else -> {
                Text(if (invite) "$email · $roleLabel" else email!!, color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                ErrorText(error)
                if (error != null) Spacer(Modifier.height(10.dp))
                Field(password, { password = it }, "New password", password = true, enabled = !busy)
                Text("At least 8 characters.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                Spacer(Modifier.height(10.dp))
                Field(again, { again = it }, "Repeat password", password = true, enabled = !busy)
                Spacer(Modifier.height(16.dp))
                if (invite) {
                    Agreement(agreed, enabled = !busy, onLegal = onLegal) { agreed = it }
                    Spacer(Modifier.height(12.dp))
                }
                GoldButton(
                    if (busy) "Saving…" else if (invite) "Create account" else "Save password",
                    Modifier.fillMaxWidth(),
                    enabled = !busy && password.length >= 8 && (!invite || agreed),
                ) {
                    if (password != again) {
                        error = "The passwords do not match."
                        return@GoldButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            if (invite) {
                                val r = app.api.post("/api/auth/invite/$token", LoginResponse.serializer()) {
                                    put("password", password)
                                    put("platform", "android")
                                    put("acceptTerms", true)
                                }
                                onSignedIn(r.token ?: throw IllegalStateException("No session returned."))
                            } else {
                                app.api.post("/api/auth/reset/$token", Ok.serializer()) { put("password", password) }
                                done = true
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save."
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

/** "I agree to the Terms and Conditions and the Privacy Policy", one box, both documents linked. */
@Composable
private fun Agreement(checked: Boolean, enabled: Boolean, onLegal: (String) -> Unit, onChange: (Boolean) -> Unit) {
    val links = TextLinkStyles(SpanStyle(color = Gold, textDecoration = TextDecoration.Underline))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Night, uncheckedColor = SnowFaint),
        )
        Text(
            buildAnnotatedString {
                append("I agree to the ")
                withLink(LinkAnnotation.Clickable("terms", links) { onLegal("terms") }) { append("Terms and Conditions") }
                append(" and the ")
                withLink(LinkAnnotation.Clickable("privacy", links) { onLegal("privacy") }) { append("Privacy Policy") }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = SnowSoft,
        )
    }
}
