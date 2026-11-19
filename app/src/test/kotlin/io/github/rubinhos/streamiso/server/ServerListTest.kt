package io.github.rubinhos.streamiso.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerListTest {

    private val home = ServerAddress("http", "192.168.1.4", 8080)
    private val away = ServerAddress("http", "100.91.189.120", 8080)

    @Test
    fun automaticTriesTheFirstAddressThenTheSecond() {
        assertEquals(listOf(home, away), ServerList(home, away).candidates)
    }

    @Test
    fun aChoiceUsesOnlyThatAddress() {
        assertEquals(listOf(home), ServerList(home, away, ServerChoice.FIRST).candidates)
        assertEquals(listOf(away), ServerList(home, away, ServerChoice.SECOND).candidates)
    }

    @Test
    fun withoutASecondAddressOnlyTheFirstIsUsed() {
        assertEquals(listOf(home), ServerList(home).candidates)
        assertEquals(listOf(home), ServerList(home, null, ServerChoice.SECOND).candidates)
    }

    @Test
    fun switchesToTheOtherAddress() {
        val servers = ServerList(home, away)
        assertEquals(away, servers.other(home))
        assertEquals(home, servers.other(away))
        assertNull(ServerList(home).other(home))
        assertEquals(ServerChoice.SECOND, servers.choosing(away).choice)
        assertEquals(ServerChoice.FIRST, servers.choosing(home).choice)
    }

    @Test
    fun readsTheSavedChoice() {
        assertEquals(ServerChoice.SECOND, ServerChoice.parse("SECOND"))
        assertEquals(ServerChoice.AUTO, ServerChoice.parse(null))
        assertEquals(ServerChoice.AUTO, ServerChoice.parse("something else"))
    }
}
