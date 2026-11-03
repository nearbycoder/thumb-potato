package com.nearbycoder.thumbpotato.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val status: Int) : IOException(messageFor(status)) {
    companion object {
        fun messageFor(status: Int): String = when (status) {
            401 -> "That RAWG key was rejected. Double-check it in Settings."
            404 -> "We couldn't find that game."
            429 -> "Too many requests. Try again in a moment."
            503 -> "Couldn't reach the game catalog. Check your connection."
            else -> "The server returned an error ($status)."
        }
    }
}

class MissingKeyException : IOException("Add a RAWG key in Settings to open this game.")

/** One shared OkHttp client with an on-disk cache, plus a lenient JSON decoder. */
object Http {
    const val USER_AGENT = "ThumbPotato/1.0 (https://github.com/nearbycoder/thumb-potato)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Volatile
    private var clientInstance: OkHttpClient? = null

    val client: OkHttpClient
        get() = clientInstance ?: synchronized(this) {
            clientInstance ?: buildClient(null).also { clientInstance = it }
        }

    fun init(context: Context) {
        synchronized(this) {
            if (clientInstance == null) {
                clientInstance = buildClient(File(context.cacheDir, "http"))
            }
        }
    }

    // CheapShark and Wikimedia reject generic agents, so every request (images too) carries ours.
    private fun buildClient(cacheDir: File?): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply { if (cacheDir != null) cache(Cache(cacheDir, 64L * 1024 * 1024)) }
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val response = client.newCall(request).await()
        response.use {
            if (!it.isSuccessful) throw HttpException(it.code)
            return withContext(Dispatchers.IO) { it.body.string() }
        }
    }

    suspend inline fun <reified T> get(url: String, headers: Map<String, String> = emptyMap()): T =
        json.decodeFromString(getString(url, headers))

    fun url(base: String, path: String, query: Map<String, String?> = emptyMap()): String {
        val builder: HttpUrl.Builder = (base + path).toHttpUrl().newBuilder()
        query.entries.filter { it.value != null }.sortedBy { it.key }.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}

object DateParsing {
    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun date(string: String?): LocalDate? {
        if (string.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(string.take(10), dayFormat) }.getOrNull()
    }

    fun localDate(epochMillis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
}
