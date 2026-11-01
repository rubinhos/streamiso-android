package io.github.rubinhos.streamiso.server

import android.content.Context

/** Remembers the server address on this device. */
class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    fun load(): ServerAddress? = prefs.getString(KEY_BASE_URL, null)?.let(ServerAddress::parse)

    fun save(address: ServerAddress) {
        prefs.edit().putString(KEY_BASE_URL, address.baseUrl).apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
    }
}
