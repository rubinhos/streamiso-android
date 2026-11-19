package io.github.rubinhos.streamiso.server

import android.content.Context

/** Remembers the server's addresses on this device. */
class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    fun load(): ServerList? {
        val first = prefs.getString(KEY_BASE_URL, null)?.let(ServerAddress::parse) ?: return null
        val second = prefs.getString(KEY_SECOND_URL, null)?.let(ServerAddress::parse)
        return ServerList(first, second, ServerChoice.parse(prefs.getString(KEY_CHOICE, null)))
    }

    fun save(servers: ServerList) {
        prefs.edit()
            .putString(KEY_BASE_URL, servers.first.baseUrl)
            .putString(KEY_SECOND_URL, servers.second?.baseUrl)
            .putString(KEY_CHOICE, servers.choice.name)
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_SECOND_URL = "second_url"
        const val KEY_CHOICE = "choice"
    }
}
