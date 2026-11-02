package com.sozluk.ai.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sozluk.ai.R

/** Which explanation dialog is currently visible during onboarding (null = none). */
private enum class OnboardingDialog {
    Intro, MediaRationale, PartialMedia, OverlayRationale, DeniedMedia, DeniedOverlay, DeniedNotifications
}

/**
 * The "Screenshot watcher" Settings row (task 10): a Material3 switch plus the stepwise permission
 * onboarding that runs when turning it ON. The switch only flips on once every permission is
 * granted (via [onSetEnabled]`(true)`); any denial aborts and leaves it off with an explanation.
 * Turning it OFF calls [onSetEnabled]`(false)` immediately.
 */
@Composable
fun ScreenshotWatcherRow(
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<OnboardingDialog?>(null) }

    fun close() { dialog = null }
    fun commit() { close(); onSetEnabled(true) }

    // --- Permission helpers ---
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFullMediaAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun isPartialMediaAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !granted(Manifest.permission.READ_MEDIA_IMAGES) &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // --- Launchers & steps (declared in dependency order to avoid forward references) ---
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) commit() else dialog = OnboardingDialog.DeniedNotifications
    }

    // Notifications is the final step: pre-33 or already-granted commits immediately.
    fun proceedToNotifications() =
        stepNotifications(context, notificationLauncher::launch) { commit() }

    // Overlay granted → straight to notifications; missing → show the overlay rationale dialog.
    fun proceedToOverlayThenOn() {
        val pending = stepOverlay(context) { dialog = it }
        if (pending == null) proceedToNotifications()
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        // ACTION_MANAGE_OVERLAY_PERMISSION returns no result code: re-check the actual state.
        if (Settings.canDrawOverlays(context)) proceedToNotifications()
        else dialog = OnboardingDialog.DeniedOverlay
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Trust the fresh self-check, not the result map (A14 "selected photos" is misleading).
        when {
            hasFullMediaAccess() -> proceedToOverlayThenOn()
            isPartialMediaAccess() -> dialog = OnboardingDialog.PartialMedia
            else -> dialog = OnboardingDialog.DeniedMedia
        }
    }

    fun startOnboarding() { dialog = OnboardingDialog.Intro }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.settings_screenshot_watcher_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.settings_screenshot_watcher_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { wantOn ->
                    if (wantOn) startOnboarding() else onSetEnabled(false)
                },
            )
        }
    }

    // --- Dialogs ---
    when (dialog) {
        OnboardingDialog.Intro -> ConfirmDialog(
            title = stringResource(R.string.screenshot_onboarding_intro_title),
            message = stringResource(R.string.screenshot_onboarding_intro_message),
            confirmText = stringResource(R.string.screenshot_onboarding_continue),
            dismissText = stringResource(R.string.screenshot_onboarding_cancel),
            onConfirm = {
                if (hasFullMediaAccess()) { close(); proceedToOverlayThenOn() }
                else dialog = OnboardingDialog.MediaRationale
            },
            onDismiss = ::close,
        )

        OnboardingDialog.MediaRationale -> ConfirmDialog(
            title = stringResource(R.string.screenshot_perm_media_rationale_title),
            message = stringResource(R.string.screenshot_perm_media_rationale_message),
            confirmText = stringResource(R.string.screenshot_onboarding_continue),
            dismissText = stringResource(R.string.screenshot_onboarding_cancel),
            onConfirm = { close(); mediaLauncher.launch(mediaPermissions()) },
            onDismiss = ::close,
        )

        OnboardingDialog.OverlayRationale -> ConfirmDialog(
            title = stringResource(R.string.screenshot_perm_overlay_rationale_title),
            message = stringResource(R.string.screenshot_perm_overlay_rationale_message),
            confirmText = stringResource(R.string.screenshot_perm_overlay_open_settings),
            dismissText = stringResource(R.string.screenshot_onboarding_cancel),
            onConfirm = {
                close()
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            onDismiss = ::close,
        )

        OnboardingDialog.PartialMedia -> NoticeDialog(
            title = stringResource(R.string.screenshot_perm_media_partial_title),
            message = stringResource(R.string.screenshot_perm_media_partial_message),
            onDismiss = ::close,
        )

        OnboardingDialog.DeniedMedia -> NoticeDialog(
            title = stringResource(R.string.screenshot_perm_denied_title),
            message = stringResource(R.string.screenshot_perm_denied_message),
            onDismiss = ::close,
        )

        OnboardingDialog.DeniedOverlay -> NoticeDialog(
            title = stringResource(R.string.screenshot_perm_denied_title),
            message = stringResource(R.string.screenshot_perm_denied_message),
            onDismiss = ::close,
        )

        OnboardingDialog.DeniedNotifications -> NoticeDialog(
            title = stringResource(R.string.screenshot_perm_denied_title),
            message = stringResource(R.string.screenshot_perm_notifications_rationale_message),
            onDismiss = ::close,
        )

        null -> Unit
    }
}

/**
 * Overlay step: if the overlay permission is missing, ask the caller to show the rationale dialog
 * (returns that dialog); if already granted, returns null so the caller proceeds to notifications.
 */
private fun stepOverlay(
    context: android.content.Context,
    showDialog: (OnboardingDialog) -> Unit,
): OnboardingDialog? {
    return if (Settings.canDrawOverlays(context)) {
        null
    } else {
        OnboardingDialog.OverlayRationale.also(showDialog)
    }
}

/**
 * Notifications step: pre-33 there is no runtime prompt, so commit; on 33+ request the permission
 * (its launcher callback finishes the flow) unless already granted.
 */
private fun stepNotifications(
    context: android.content.Context,
    launch: (String) -> Unit,
    onAlreadyGranted: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onAlreadyGranted()
        return
    }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) onAlreadyGranted() else launch(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

@Composable
private fun NoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}
