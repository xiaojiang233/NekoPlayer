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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInputs = listOf(
            RemoteInput.Builder(remoteInputKey)
                .setLabel(label)
                .build()
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        launcher.launch(intent)
    } else {
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
