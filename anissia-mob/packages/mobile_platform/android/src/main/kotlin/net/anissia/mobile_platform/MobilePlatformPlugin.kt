package net.anissia.mobile_platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.net.URI

class MobilePlatformPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    ActivityAware, PluginRegistry.NewIntentListener {
    private lateinit var context: Context
    private var channel: MethodChannel? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var pendingLink: String? = null
    private var listenerReady = false
    private var initialIntentRead = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, CHANNEL).also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        detachActivity()
        channel?.setMethodCallHandler(null)
        channel = null
        listenerReady = false
        pendingLink = null
        initialIntentRead = false
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        attachActivity(binding)
        if (!initialIntentRead) {
            initialIntentRead = true
            receiveIntent(binding.activity.intent)
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        attachActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() = detachActivity()

    override fun onDetachedFromActivity() = detachActivity()

    private fun attachActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addOnNewIntentListener(this)
    }

    private fun detachActivity() {
        activityBinding?.removeOnNewIntentListener(this)
        activityBinding = null
    }

    override fun onNewIntent(intent: Intent): Boolean = receiveIntent(intent)

    private fun receiveIntent(intent: Intent?): Boolean {
        if (context.packageName != SCHEDULE_PACKAGE || intent?.action != Intent.ACTION_VIEW) {
            return false
        }
        val url = scheduleUri(intent.dataString)?.toString() ?: return false
        if (listenerReady) {
            channel?.invokeMethod("link", mapOf("url" to url))
        } else {
            pendingLink = url
        }
        return true
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialLink" -> {
                val initial = pendingLink
                pendingLink = null
                listenerReady = true
                result.success(initial)
            }
            "openBrowser" -> result.success(openBrowser(call.argument<String>("url")))
            "openSchedule" -> result.success(openSchedule(call.argument<String>("url")))
            "openExternal" -> result.success(openExternal(call.argument<String>("url")))
            "exitApp" -> {
                result.success(null)
                activityBinding?.activity?.finish()
            }
            else -> result.notImplemented()
        }
    }

    private fun openSchedule(raw: String?): Boolean {
        if (context.packageName == SCHEDULE_PACKAGE) return false
        val url = scheduleUri(raw) ?: return false
        if (launch(
            Intent(Intent.ACTION_VIEW, url)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(SCHEDULE_PACKAGE)
        )) return true
        val launcher = context.packageManager.getLaunchIntentForPackage(SCHEDULE_PACKAGE)
            ?: return false
        launcher.data = url
        return launch(launcher)
    }

    private fun openExternal(raw: String?): Boolean {
        if (raw.isNullOrEmpty() || raw.length > 16384 || raw.any { it <= ' ' || it == '\\' }) {
            return false
        }
        val parsed = try {
            URI(raw)
        } catch (_: java.net.URISyntaxException) {
            return false
        }
        val scheme = parsed.scheme?.lowercase()
        if (scheme !in setOf("mailto", "tel") || parsed.rawAuthority != null ||
            parsed.rawFragment != null || parsed.rawSchemeSpecificPart.isNullOrEmpty() ||
            parsed.rawSchemeSpecificPart.startsWith("//")
        ) return false
        if (scheme == "tel" && parsed.rawSchemeSpecificPart.substringBefore('?').isEmpty()) {
            return false
        }
        val action = if (scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_DIAL
        return launch(Intent(action, Uri.parse(parsed.toASCIIString())))
    }

    @Suppress("DEPRECATION")
    private fun openBrowser(raw: String?): Boolean {
        val url = webUri(raw) ?: return false
        val manager = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://browser.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        val browsers = manager.queryIntentActivities(probe, flags)
            .filter { it.activityInfo.exported && it.filter?.countDataAuthorities() == 0 }
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName && it != SCHEDULE_PACKAGE }
            .distinct()
        if (browsers.isEmpty()) return false

        val defaultPackage = manager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val selectedPackage = defaultPackage?.takeIf { it in browsers }
            ?: browsers.singleOrNull()
        val base = Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)
        if (selectedPackage != null) return launch(base.setPackage(selectedPackage))

        val choices = browsers.map { Intent(base).setPackage(it) }
        val chooser = Intent.createChooser(choices.first(), "브라우저 선택")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, choices.drop(1).toTypedArray())
        return launch(chooser)
    }

    private fun launch(intent: Intent): Boolean {
        val activity: Activity? = activityBinding?.activity
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            (activity ?: context).startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun webUri(raw: String?): Uri? {
        if (raw.isNullOrEmpty() || raw.length > 16384 || raw.any { it <= ' ' || it == '\\' }) {
            return null
        }
        val parsed = try {
            URI(raw)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: java.net.URISyntaxException) {
            return null
        }
        if (parsed.scheme?.lowercase() !in setOf("http", "https") ||
            parsed.host.isNullOrEmpty() || parsed.rawUserInfo != null ||
            parsed.port !in -1..65535 || parsed.port == 0
        ) return null
        return Uri.parse(parsed.toASCIIString())
    }

    private fun scheduleUri(raw: String?): Uri? {
        val url = webUri(raw) ?: return null
        if (url.scheme?.lowercase() != "https" ||
            !url.host.equals("anissia.net", ignoreCase = true) ||
            (url.port != -1 && url.port != 443)
        ) return null
        val path = url.path ?: return null
        if (path != "/schedule" && !path.startsWith("/schedule/")) return null
        if (path.contains('\\') || path.split('/').any { it == "." || it == ".." }) return null
        return url.buildUpon().scheme("https").encodedAuthority("anissia.net").build()
    }

    companion object {
        private const val CHANNEL = "net.anissia.mobile/platform"
        private const val SCHEDULE_PACKAGE = "anissia.android.schedule"
    }
}
