package top.xiaojiang233.nekoplayer.utils

import android.app.AlertDialog
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.wear.input.RemoteInputIntentHelper

fun launchTextInput(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    remoteInputKey: String,
    label: String,
    initialValue: String? = null,
    onResult: (String) -> Unit
) {
    // WearOS 2.0+ generally supports remote input intent
    // Using a more inclusive check or just trying the intent
    try {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInput = RemoteInput.Builder(remoteInputKey)
            .setLabel(label)
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        launcher.launch(intent)
    } catch (e: Exception) {
        // Fallback to AlertDialog if intent is not supported
        val editText = EditText(context).apply {
            if (initialValue != null) {
                setText(initialValue)
            }
        }
        AlertDialog.Builder(context)
            .setTitle(label)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val result = editText.text.toString()
                if (result.isNotBlank()) {
                    onResult(result)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
