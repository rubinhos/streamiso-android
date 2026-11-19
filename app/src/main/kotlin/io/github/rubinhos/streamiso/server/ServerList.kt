package io.github.rubinhos.streamiso.server

/** Which saved address the app uses. */
enum class ServerChoice {
    /** The first address, and the second one when the first does not answer. */
    AUTO,
    FIRST,
    SECOND,
    ;

    companion object {
        fun parse(value: String?): ServerChoice = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}

/**
 * The addresses of one StreamISO server: the usual one, and optionally a second way to reach it, such as its
 * Tailscale address away from home. Plain Kotlin, so that it can be unit tested.
 */
data class ServerList(val first: ServerAddress, val second: ServerAddress? = null, val choice: ServerChoice = ServerChoice.AUTO) {

    /** The addresses to try, in order. A choice of the second address without one falls back to the first. */
    val candidates: List<ServerAddress>
        get() = when {
            second == null || choice == ServerChoice.FIRST -> listOf(first)
            choice == ServerChoice.SECOND -> listOf(second)
            else -> listOf(first, second)
        }

    /** The other saved address, to switch to; null without a second one. */
    fun other(current: ServerAddress): ServerAddress? = when {
        second == null -> null
        current == second -> first
        else -> second
    }

    /** The same list, always using [address] (which must be one of the two). */
    fun choosing(address: ServerAddress): ServerList = copy(choice = if (address == second) ServerChoice.SECOND else ServerChoice.FIRST)
}
