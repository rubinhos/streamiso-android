package io.github.rubinhos.streamiso.server

import java.net.URI
import java.net.URISyntaxException

/** Which part of the StreamISO web interface a URL points to. */
enum class Route { CATALOG, PLAYER, OTHER }

/**
 * The address of a StreamISO server, and the rules for what the app may load from it.
 * Plain Kotlin with no Android classes, so that it can be unit tested and reused by a native client.
 */
data class ServerAddress(val scheme: String, val host: String, val port: Int) {

    val baseUrl: String get() = "$scheme://$host:$port"

    /** The address as the user would type it. */
    val display: String get() = if (scheme == "http") "$host:$port" else baseUrl

    fun url(path: String): String = baseUrl + path

    /** Top-level pages must come from this exact server. */
    fun allowsNavigation(url: String): Boolean {
        val uri = parseUri(url) ?: return false
        return isSameHost(uri) && uri.scheme.equals(scheme, ignoreCase = true) && effectivePort(uri) == port
    }

    /**
     * Frames and resources may come from any port of the same host, because the player page embeds
     * the video stream (Neko) from another port. Anything on another host is refused.
     */
    fun allowsResource(url: String): Boolean {
        val uri = parseUri(url) ?: return false
        return when (uri.scheme?.lowercase()) {
            "data", "blob", "about" -> true
            "http", "https" -> isSameHost(uri)
            else -> false
        }
    }

    fun routeOf(url: String): Route {
        if (!allowsNavigation(url)) return Route.OTHER
        return when (parseUri(url)?.path.orEmpty().trimEnd('/')) {
            "" -> Route.CATALOG
            "/player" -> Route.PLAYER
            else -> Route.OTHER
        }
    }

    private fun isSameHost(uri: URI) = uri.host.equals(host, ignoreCase = true)

    private fun effectivePort(uri: URI) = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    companion object {
        /** The port StreamISO uses unless configured otherwise. */
        private const val DEFAULT_PORT = 8080

        /** Accepts "192.168.0.10:8080", "http://server.local:8080/" and so on. Returns null if invalid. */
        fun parse(input: String): ServerAddress? {
            val text = input.trim()
            if (text.isEmpty()) return null
            val uri = parseUri(if ("://" in text) text else "http://$text") ?: return null
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (host.isNullOrEmpty() || uri.userInfo != null) return null
            // StreamISO always lives at the root of its address.
            if (uri.path.orEmpty().trimEnd('/').isNotEmpty() || uri.query != null || uri.fragment != null) return null
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                else -> DEFAULT_PORT
            }
            if (port !in 1..65535) return null
            return ServerAddress(scheme, host, port)
        }

        private fun parseUri(text: String): URI? = try {
            URI(text)
        } catch (_: URISyntaxException) {
            null
        }
    }
}
