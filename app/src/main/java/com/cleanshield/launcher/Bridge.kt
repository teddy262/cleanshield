package com.cleanshield.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * Android implementation of CleanFrame's FrameDevice API.
 * Every method takes a callback id and answers through window.__fdCb(id, err, value).
 * App icons are served to the page at /appicon/<package>.png (CleanFrame only accepts
 * icon paths that start with "/").
 */
class Bridge(private val activity: Activity, private val web: WebView) {

    // brand name used by CleanFrame -> Android package. Order = display rank.
    private val brands = linkedMapOf(
        "stremio" to "com.stremio.one",
        "netflix" to "com.netflix.ninja",
        "disneyplus" to "com.disney.disneyplus",
        "youtube" to "com.google.android.youtube.tv",
        "appletv" to "com.apple.atve.androidtv.appletv",
        "primevideo" to "com.amazon.amazonvideo.livingroom",
        "spotify" to "com.spotify.tv.android",
        "hbomax" to "com.wbd.stream",
        "tubi" to "com.tubitv"
    )

    // Fallback: recognise a brand by the app's visible name if its package differs.
    private val brandLabels = mapOf(
        "stremio" to listOf("stremio"),
        "netflix" to listOf("netflix"),
        "disneyplus" to listOf("disney+", "disney plus"),
        "youtube" to listOf("youtube"),
        "appletv" to listOf("apple tv", "apple tv+"),
        "primevideo" to listOf("prime video", "amazon prime video"),
        "spotify" to listOf("spotify"),
        "hbomax" to listOf("max", "hbo max"),
        "tubi" to listOf("tubi", "tubi tv")
    )

    private val brandByPkg: Map<String, String> = brands.entries.associate { it.value to it.key }
    private val iconCache = HashMap<String, ByteArray>()

    private fun brandFor(pkg: String, label: String): String {
        brandByPkg[pkg]?.let { return it }
        val l = label.trim().lowercase()
        for ((brand, names) in brandLabels) {
            if (l in names) return brand
        }
        return ""
    }

    private fun error(message: String, detail: String? = null): JSONObject {
        val o = JSONObject().put("message", message)
        if (detail != null) o.put("detail", detail)
        return o
    }

    private fun reply(id: Int, err: JSONObject?, valueJson: String?) {
        val e = err?.toString() ?: "null"
        val v = valueJson ?: "null"
        activity.runOnUiThread {
            web.evaluateJavascript("window.__fdCb($id,$e,$v)", null)
        }
    }

    // ---- Icons -------------------------------------------------------------

    private fun render(d: Drawable): ByteArray {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun cacheIcon(pkg: String, d: Drawable) {
        val bytes = render(d)
        synchronized(iconCache) { iconCache[pkg] = bytes }
    }

    private fun renderPackage(pkg: String): ByteArray? {
        return try {
            val bytes = render(activity.packageManager.getApplicationIcon(pkg))
            synchronized(iconCache) { iconCache[pkg] = bytes }
            bytes
        } catch (ex: Exception) {
            null
        }
    }

    /** Called by the WebView for /appicon/<package>.png requests. */
    fun iconResponse(path: String): WebResourceResponse? {
        val pkg = path.removeSuffix(".png")
        val cached = synchronized(iconCache) { iconCache[pkg] }
        val bytes: ByteArray = cached ?: renderPackage(pkg) ?: return null
        return WebResourceResponse("image/png", null, ByteArrayInputStream(bytes))
    }

    // ---- Launching ---------------------------------------------------------

    private fun launchPackage(pkg: String): JSONObject? {
        val pm = activity.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return error("This app isn't installed on the Shield.", pkg)
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            null
        } catch (ex: Exception) {
            error("The Shield could not open this app.", ex.message)
        }
    }

    private fun launchOnUi(id: Int, pkg: String) {
        activity.runOnUiThread {
            val err = launchPackage(pkg)
            reply(id, err, null)
        }
    }

    @JavascriptInterface
    fun launch(id: Int, brand: String) {
        val pkg = brands[brand]
        if (pkg == null) {
            reply(id, error("This app has no launch target."), null)
            return
        }
        launchOnUi(id, pkg)
    }

    @JavascriptInterface
    fun launchApp(id: Int, pkg: String) {
        launchOnUi(id, pkg)
    }

    @JavascriptInterface
    fun art(id: Int, arg: String) {
        reply(id, error("Art Mode is a Samsung Frame feature and isn't available on the Shield."), null)
    }

    // "Home" opens the stock Shield home screen, if it is still enabled.
    @JavascriptInterface
    fun home(id: Int, arg: String) {
        activity.runOnUiThread {
            val pm = activity.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val other = pm.queryIntentActivities(query, 0)
                .firstOrNull { it.activityInfo.packageName != activity.packageName }
            if (other == null) {
                reply(
                    id,
                    error(
                        "The stock Shield home screen is disabled.",
                        "Re-enable it with: adb shell pm enable <launcher package>"
                    ),
                    null
                )
                return@runOnUiThread
            }
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClassName(other.activityInfo.packageName, other.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
                reply(id, null, null)
            } catch (ex: Exception) {
                reply(id, error("The Shield could not open its home screen.", ex.message), null)
            }
        }
    }

    @JavascriptInterface
    fun settings(id: Int, arg: String) {
        activity.runOnUiThread {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                reply(id, null, null)
            } catch (ex: Exception) {
                reply(id, error("The Shield could not open Settings.", ex.message), null)
            }
        }
    }

    // ---- App list ----------------------------------------------------------

    @JavascriptInterface
    fun apps(id: Int, arg: String) {
        thread {
            try {
                val pm = activity.packageManager
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                val brandOrder = brands.keys.toList()
                val seen = HashSet<String>()
                val rows = ArrayList<JSONObject>()

                for (ri in pm.queryIntentActivities(query, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == activity.packageName || !seen.add(pkg)) continue
                    val label = ri.loadLabel(pm).toString()
                    val brand = brandFor(pkg, label)
                    val rank = if (brand.isEmpty()) 30 else brandOrder.indexOf(brand)
                    cacheIcon(pkg, ri.loadIcon(pm))
                    rows.add(
                        JSONObject()
                            .put("appId", pkg)
                            .put("name", label)
                            .put("brand", brand)
                            .put("icon", "/appicon/$pkg.png")
                            .put("rank", rank)
                    )
                }

                rows.sortWith(
                    compareBy<JSONObject>({ it.getInt("rank") }, { it.getString("name").lowercase() })
                )
                val arr = JSONArray()
                rows.forEach { arr.put(it) }
                reply(id, null, arr.toString())
            } catch (ex: Exception) {
                reply(id, error("The Shield could not list its apps.", ex.message), null)
            }
        }
    }
}
