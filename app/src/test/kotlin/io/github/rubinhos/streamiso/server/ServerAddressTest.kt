package io.github.rubinhos.streamiso.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {

    private val server = ServerAddress("http", "192.168.0.10", 8080)

    @Test
    fun parsesTheFormsPeopleType() {
        assertEquals(server, ServerAddress.parse("192.168.0.10:8080"))
        assertEquals(server, ServerAddress.parse("  http://192.168.0.10:8080/  "))
        assertEquals(server, ServerAddress.parse("192.168.0.10"))
        assertEquals(ServerAddress("http", "server.local", 8080), ServerAddress.parse("SERVER.local"))
        assertEquals(ServerAddress("https", "server.local", 443), ServerAddress.parse("https://server.local"))
        assertEquals(ServerAddress("http", "[fe80::1]", 8080), ServerAddress.parse("[fe80::1]:8080"))
    }

    @Test
    fun rejectsWhatIsNotAServerAddress() {
        listOf(
            "",
            "   ",
            "not an address",
            "ftp://192.168.0.10",
            "192.168.0.10:99999",
            "192.168.0.10:8080/player",
            "192.168.0.10:8080/?a=b",
            "user@192.168.0.10:8080",
        ).forEach { assertNull(it, ServerAddress.parse(it)) }
    }

    @Test
    fun survivesBeingSavedAndLoaded() {
        assertEquals(server, ServerAddress.parse(server.baseUrl))
        assertEquals(server, ServerAddress.parse(server.display))
    }

    @Test
    fun pagesMustComeFromTheExactServer() {
        assertTrue(server.allowsNavigation("http://192.168.0.10:8080/"))
        assertTrue(server.allowsNavigation("http://192.168.0.10:8080/player"))
        assertFalse(server.allowsNavigation("http://192.168.0.10:8081/"))
        assertFalse(server.allowsNavigation("https://192.168.0.10:8080/"))
        assertFalse(server.allowsNavigation("http://192.168.0.11:8080/"))
        assertFalse(server.allowsNavigation("https://example.com/"))
        assertFalse(server.allowsNavigation("http://192.168.0.10:8080@example.com/"))
        assertFalse(server.allowsNavigation("http://192.168.0.10.example.com:8080/"))
        assertFalse(server.allowsNavigation("file:///etc/hosts"))
        assertFalse(server.allowsNavigation("not a url"))
    }

    @Test
    fun theVideoStreamMayComeFromAnotherPortOfTheSameHost() {
        assertTrue(server.allowsResource("http://192.168.0.10:8081/?usr=tv&pwd=secret&cast=1"))
        assertTrue(server.allowsResource("http://192.168.0.10:8080/static/app.js"))
        assertTrue(server.allowsResource("data:image/png;base64,AAAA"))
        assertTrue(server.allowsResource("blob:http://192.168.0.10:8080/4d9a"))
        assertFalse(server.allowsResource("https://example.com/font.woff2"))
        assertFalse(server.allowsResource("http://192.168.0.11:8081/"))
        assertFalse(server.allowsResource("file:///etc/hosts"))
    }

    @Test
    fun recognizesTheRoutes() {
        assertEquals(Route.CATALOG, server.routeOf("http://192.168.0.10:8080"))
        assertEquals(Route.CATALOG, server.routeOf("http://192.168.0.10:8080/"))
        assertEquals(Route.CATALOG, server.routeOf("http://192.168.0.10:8080/?x=1"))
        assertEquals(Route.PLAYER, server.routeOf("http://192.168.0.10:8080/player"))
        assertEquals(Route.PLAYER, server.routeOf("http://192.168.0.10:8080/player/"))
        assertEquals(Route.OTHER, server.routeOf("http://192.168.0.10:8080/players"))
        assertEquals(Route.OTHER, server.routeOf("http://192.168.0.10:8081/player"))
        assertEquals(Route.OTHER, server.routeOf("https://example.com/"))
    }
}
