package com.arkhins.wink.data

import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ConnectionPool
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** The server answered with an error the person should see. 401 means "sign in again"; [reason] "banned" means the account is gone for good. */
class ApiException(val code: Int, message: String, val reason: String? = null) : IOException(message)

/**
 * The Wink server, as the app sees it. Every call carries the session
 * token as a bearer header; errors come back as [ApiException] with the
 * server's own wording, or [NoConnectionException] when nothing answered.
 */
class WinkApi(private val session: SessionStore) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /*
     * Connections are reused across calls. A reused HTTP/2 connection that
     * died while the phone slept would otherwise stall every request on it
     * for the whole read timeout, so idle connections are dropped after a
     * short while and live ones are pinged, which finds a dead one in
     * seconds instead of a minute.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 20, TimeUnit.SECONDS))
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val token = session.token
            // Only our own server gets the token — never a storage bucket a download redirects to.
            val ours = request.url.toString().startsWith(Config.BASE_URL)
            chain.proceed(
                if (token != null && ours) request.newBuilder().header("Authorization", "Bearer $token").build() else request,
            )
        }
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun url(path: String): String = if (path.startsWith("http")) path else Config.BASE_URL + path

    /** A relative `/api/...` URL from the server as an absolute one. */
    fun absolute(path: String?): String? = path?.let { url(it) }

    /**
     * Whether the app is in sync with the server: false after a request that
     * could not get through or that the server failed (5xx), true again on
     * the next one that works. The no-connection icon follows it.
     */
    val online = MutableStateFlow(true)

    private fun execute(request: Request): Response {
        val started = System.currentTimeMillis()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            Log.w(TAG, "${request.method} ${request.url.encodedPath} failed after ${System.currentTimeMillis() - started} ms: ${e.message}")
            online.value = false
            throw NoConnectionException(e)
        }
        online.value = response.code < 500
        Log.d(TAG, "${request.method} ${request.url.encodedPath} -> ${response.code} in ${System.currentTimeMillis() - started} ms")
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val message = obj?.get("error")?.jsonPrimitive?.content
            val reason = runCatching { obj?.get("code")?.jsonPrimitive?.content }.getOrNull()
            throw ApiException(response.code, message ?: "Request failed (${response.code}).", reason)
        }
        return response
    }

    private fun builder(path: String) = Request.Builder()
        .url(url(path))
        .header("Accept", "application/json")
        .header("User-Agent", "${Config.APP_NAME} Android/${BuildConfig.VERSION_NAME}")

    suspend fun <T> get(path: String, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        execute(builder(path).get().build()).use { json.decodeFromString(serializer, it.body!!.string()) }
    }

    /** The answer as it came, for the phone's copy. */
    suspend fun getText(path: String): String = withContext(Dispatchers.IO) {
        execute(builder(path).get().build()).use { it.body!!.string() }
    }

    suspend fun <T> send(method: String, path: String, body: JsonObject, serializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val request = builder(path).method(method, body.toString().toRequestBody(jsonType)).build()
            execute(request).use { json.decodeFromString(serializer, it.body!!.string()) }
        }

    suspend fun <T> post(path: String, serializer: KSerializer<T>, build: JsonObjectBuilderScope = {}): T =
        send("POST", path, buildJsonObject(build), serializer)

    suspend fun <T> patch(path: String, serializer: KSerializer<T>, build: JsonObjectBuilderScope = {}): T =
        send("PATCH", path, buildJsonObject(build), serializer)

    suspend fun <T> put(path: String, serializer: KSerializer<T>, build: JsonObjectBuilderScope = {}): T =
        send("PUT", path, buildJsonObject(build), serializer)

    suspend fun delete(path: String, build: JsonObjectBuilderScope = {}): Ok =
        send("DELETE", path, buildJsonObject(build), Ok.serializer())

    /** A multipart POST: the profile form with its photo. */
    suspend fun <T> postForm(path: String, fields: Map<String, String>, file: Pair<String, File>?, mime: String, serializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            fields.forEach { (k, v) -> form.addFormDataPart(k, v) }
            if (file != null) form.addFormDataPart(file.first, file.second.name, file.second.asRequestBody(mime.toMediaType()))
            execute(builder(path).post(form.build()).build()).use { json.decodeFromString(serializer, it.body!!.string()) }
        }

    /** PUT raw bytes to an upload slot: the storage bucket's signed URL, or our own server. [onProgress] gets 0f..1f as they go. */
    suspend fun putBytes(url: String, file: File, mime: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val type = mime.toMediaType()
        val body = object : RequestBody() {
            override fun contentType() = type
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                val total = file.length()
                var written = 0L
                var reported = -1
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        written += read
                        val percent = if (total > 0) (written * 100 / total).toInt() else 0
                        if (percent != reported) {
                            reported = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
        }
        execute(Request.Builder().url(url).put(body).build()).close()
    }

    /**
     * Stream a document's bytes into [out], following the server's redirect
     * to storage. [onProgress] gets 0f..1f, or -1f when the size is unknown.
     */
    suspend fun download(fileId: String, out: OutputStream, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        execute(builder("/api/files/$fileId?go=download").get().build()).use { response ->
            val body = response.body ?: throw IOException("The document came back empty.")
            val total = body.contentLength()
            var written = 0L
            var reported = -1
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    written += read
                    if (total > 0) {
                        val percent = (written * 100 / total).toInt()
                        if (percent != reported) {
                            reported = percent
                            onProgress(percent / 100f)
                        }
                    } else if (reported == -1) {
                        reported = 0
                        onProgress(-1f)
                    }
                }
            }
            out.flush()
        }
    }

    /* ─────────────────────── The calls the screens make ─────────────────── */

    suspend fun login(email: String, password: String): LoginResponse =
        post("/api/auth/login", LoginResponse.serializer()) {
            put("email", email)
            put("password", password)
            put("platform", "android")
        }

    suspend fun logout() {
        val push = session.pushToken
        runCatching { post("/api/auth/logout", Ok.serializer()) { if (push != null) put("pushToken", push) } }
    }

    suspend fun me(): Me = get("/api/me", Me.serializer())

    suspend fun registerPush(token: String): Ok =
        post("/api/push/register", Ok.serializer()) {
            put("token", token)
            put("platform", "android")
        }
}

private const val TAG = "WinkApi"

typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
