package com.toolbox.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
 * and a button. If the user has blocked the prompt ("Don't ask again"), the button opens the app's settings instead,
 * and the gate re-checks when the user comes back.
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
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var granted by rememberSaveable { mutableStateOf(check()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // After a denial, no rationale means the system won't show the dialog again.
        blocked = !ok && context.findActivity()?.shouldShowRequestPermissionRationale(permission) == false
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) { asked = true; launcher.launch(permission) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = check() }

    if (granted) {
        content()
        return
    }
    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Text(rationale, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    if (blocked) {
        Button(onClick = { context.openAppSettings() }) { Text("Open settings") }
    } else {
        Button(onClick = { launcher.launch(permission) }) { Text(buttonLabel) }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
