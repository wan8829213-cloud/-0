package com.example.yimei

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 调用后端 /api/reply（即 reply-api.js）。失败返回 null，调用方改用本地话术库。 */
object ReplyApi {
    const val DEFAULT_BASE = "https://kzzym.com" // 改成你的 HTTPS 域名，也可在 App 设置页修改
    private const val PREFS = "yimei"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun baseUrl(ctx: Context): String = prefs(ctx).getString("apiBase", DEFAULT_BASE) ?: DEFAULT_BASE
    fun aiEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("useAI", true)

    fun fetch(ctx: Context, message: String, tone: Tone, d: Detection, cb: (List<String>?) -> Unit) {
        val base = baseUrl(ctx)
        val main = Handler(Looper.getMainLooper())
        Thread {
            var result: List<String>? = null
            try {
                val c = URL("$base/api/reply").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 12000
                c.setRequestProperty("Content-Type", "application/json"); c.doOutput = true
                val body = JSONObject()
                    .put("message", message).put("tone", tone.apiName)
                    .put("intent", d.intent.label).put("project", d.project?.name ?: "")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode == 200) {
                    val arr: JSONArray = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("replies")
                    val l = (0 until arr.length()).map { arr.getString(it) }
                    if (l.isNotEmpty()) result = l
                }
                c.disconnect()
            } catch (_: Exception) { }
            main.post { cb(result) }
        }.start()
    }
}
