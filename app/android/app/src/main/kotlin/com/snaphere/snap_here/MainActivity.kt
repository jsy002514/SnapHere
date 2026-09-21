package com.snaphere.snap_here

import android.os.Build
import android.content.pm.PackageManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val systemUiChannel = "com.snaphere.snap_here/system_ui"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.snaphere.snap_here/maps")
            .setMethodCallHandler { call, result ->
                if (call.method == "configure") {
                    @Suppress("DEPRECATION")
                    val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    val key = info.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
                    result.success(key.isNotBlank() && !key.startsWith("your_"))
                } else {
                    result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, systemUiChannel)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "enterCameraFullscreen" -> {
                        enterCameraFullscreen()
                        result.success(null)
                    }
                    "exitCameraFullscreen" -> {
                        exitCameraFullscreen()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun enterCameraFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun exitCameraFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
