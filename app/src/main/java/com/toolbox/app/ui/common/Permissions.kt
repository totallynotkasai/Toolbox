package com.toolbox.app.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Shows [content] once [permission] is granted. Asks once when first shown; otherwise shows [icon], [rationale]
 * and a button. If Android has stopped showing the dialog ("Don't ask again", or a second denial), the button opens
 * the app's settings instead, and the gate re-checks when the user comes back.
 *
 * Emits straight into the caller's layout, so it is meant to sit inside a centred column such as [ToolBody].
 */
@Composable
fun PermissionGate(
    permission: String,
    icon: ImageVector,
    rationale: String,
    buttonLabel: String,
    content: @Composable () -> Unit,
) {
    val state = rememberPermission(permission)
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Never straight into the settings: that's only for a tap on the button.
        if (!state.granted && !state.blocked && !asked) { asked = true; state.request() }
    }

    if (state.granted) {
        content()
        return
    }
    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Text(rationale, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(onClick = state::request) { Text(if (state.blocked) "Open settings" else buttonLabel) }
}

/**
 * A runtime permission as it stands, re-read when the screen resumes and after each request.
 * [request] shows Android's dialog, or opens the app's settings once the dialog is [blocked].
 */
class PermissionState(val granted: Boolean, val blocked: Boolean, private val onRequest: () -> Unit) {
    fun request() = onRequest()
}

@Composable
fun rememberPermission(permission: String, onResult: (granted: Boolean) -> Unit = {}): PermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val memory = remember(context, permission) { RationaleMemory(Prefs(context.applicationContext), permission) }
    fun read() = readPermission(context, activity, permission, memory)

    var status by remember(memory) { mutableStateOf(read()) }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        status = read()
        currentOnResult(ok)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = read() }
    return PermissionState(status.granted, status.blocked) {
        if (status.blocked) context.openAppSettings() else launcher.launch(permission)
    }
}

internal data class PermissionStatus(val granted: Boolean, val blocked: Boolean)

/**
 * Android doesn't say outright whether it will still show a permission's dialog. Its "show a rationale" answer is
 * true only between a first denial and a final one, so the dialog is blocked when that answer has been seen since
 * the permission was last granted and is false now. Checking the answer alone, straight after a request, mistakes a
 * dialog dismissed without a choice (which also comes back denied, with no rationale) for a block, and sent people
 * to the settings when the dialog would still have shown.
 */
internal object PermissionRules {
    fun status(granted: Boolean, rationale: Boolean, rationaleSeen: Boolean) =
        PermissionStatus(granted = granted, blocked = !granted && !rationale && rationaleSeen)

    /** Whether the rationale answer counts as seen after this reading. Granting starts it afresh. */
    fun rationaleSeen(granted: Boolean, rationale: Boolean, wasSeen: Boolean) = when {
        granted -> false
        rationale -> true
        else -> wasSeen
    }
}

/** Remembers, per permission, whether Android has asked for a rationale since the permission was last granted. */
private class RationaleMemory(private val prefs: Prefs, permission: String) {
    private val key = "permission_rationale_seen_" + permission.substringAfterLast('.')
    var seen: Boolean
        get() = prefs.get(key, false)
        set(value) { if (value != seen) prefs.put(key, value) }
}

private fun readPermission(context: Context, activity: Activity?, permission: String, memory: RationaleMemory): PermissionStatus {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val rationale = activity?.shouldShowRequestPermissionRationale(permission) == true
    val seen = PermissionRules.rationaleSeen(granted, rationale, memory.seen)
    memory.seen = seen
    return PermissionRules.status(granted, rationale, seen)
}

/** Opens this app's page in Android's settings, or the settings app if a phone has no such page. */
fun Context.openAppSettings() = startFirst(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    Intent(Settings.ACTION_SETTINGS),
)

/**
 * Starts the first of [intents] that the phone can handle, and returns whether any could. Some phones leave out
 * settings pages, and starting one that isn't there throws.
 */
fun Context.startFirst(vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
    return false
}
