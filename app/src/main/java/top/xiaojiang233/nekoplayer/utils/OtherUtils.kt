package top.xiaojiang233.nekoplayer.utils

import android.content.Context
object OtherUtils{
fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            0
        )
        packageInfo.versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}}