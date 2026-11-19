package io.github.rubinhos.streamiso.server

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class Health { OK, NOT_STREAMISO, UNREACHABLE }

enum class DiscKind { DVD, CD }

/** The few calls the app makes by itself. All of them block, so call them off the main thread. */
class StreamIsoClient(private val address: ServerAddress) {

    /** [timeoutMillis] is shorter when another address can be tried next. */
    fun checkHealth(timeoutMillis: Int = TIMEOUT_MILLIS): Health = try {
        val (status, body) = request("GET", "/healthz", timeoutMillis)
        val ok = status == HttpURLConnection.HTTP_OK && JSONObject(body).optBoolean("ok")
        if (ok) Health.OK else Health.NOT_STREAMISO
    } catch (_: JSONException) {
        Health.NOT_STREAMISO
    } catch (_: IOException) {
        Health.UNREACHABLE
    }

    /** The kind of the disc that is open now, or null if nothing is open or the server did not answer. */
    fun currentDiscKind(): DiscKind? = try {
        val (status, body) = request("GET", "/api/player")
        val player = JSONObject(body)
        when {
            status != HttpURLConnection.HTTP_OK || player.isNull("id") -> null
            player.optString("kind") == "cd" -> DiscKind.CD
            player.optString("kind") == "dvd" -> DiscKind.DVD
            else -> null
        }
    } catch (_: JSONException) {
        null
    } catch (_: IOException) {
        null
    }

    /** Closes VLC on the server. Returns false if the server did not confirm it. */
    fun stop(): Boolean = try {
        request("POST", "/api/stop").first == HttpURLConnection.HTTP_OK
    } catch (_: IOException) {
        false
    }

    private fun request(method: String, path: String, timeoutMillis: Int = TIMEOUT_MILLIS): Pair<Int, String> {
        val connection = URL(address.url(path)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = false
            val status = connection.responseCode
            val stream = if (status < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
            return status to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * The first of the addresses to try that answers as StreamISO, with [Health.OK]. If none does, the last one
         * tried and what was wrong with it. Blocks, so call it off the main thread.
         */
        fun reach(servers: ServerList): Pair<ServerAddress, Health> {
            val list = servers.candidates
            var result = list.last() to Health.UNREACHABLE
            for ((i, address) in list.withIndex()) {
                val health = StreamIsoClient(address).checkHealth(if (i < list.lastIndex) PROBE_MILLIS else TIMEOUT_MILLIS)
                result = address to health
                if (health == Health.OK) break
            }
            return result
        }

        const val TIMEOUT_MILLIS = 5000

        /** Enough for a server that is on; short enough not to keep the viewer waiting before the second address. */
        const val PROBE_MILLIS = 3000
    }
}
