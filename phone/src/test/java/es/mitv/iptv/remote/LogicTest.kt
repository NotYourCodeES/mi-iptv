package es.mitv.iptv.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    private val channels = listOf(
        TvChannel(1, "La 1 HD", 0, null),
        TvChannel(2, "Telecinco", 0, null),
        TvChannel(6, "Fútbol Total", 1, null),
        TvChannel(16, "Cine 6", 2, null),
    )
    private val folded = channels.map { Logic.fold(it.name) }

    private fun names(query: String, filter: ChannelFilter = ChannelFilter.All, favorites: Set<String> = emptySet()) =
        Logic.filter(channels, folded, favorites, query, filter).map { it.name }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf("Fútbol Total"), names("FUTBOL"))
        assertEquals(listOf("Telecinco"), names(" telecinco "))
    }

    @Test
    fun anExactNumberGoesFirst() {
        assertEquals(listOf("Fútbol Total", "Cine 6"), names("6"))
    }

    @Test
    fun filtersByFavoritesAndGroup() {
        assertEquals(listOf("Telecinco"), names("", ChannelFilter.Favorites, setOf("Telecinco")))
        assertEquals(listOf("La 1 HD", "Telecinco"), names("", ChannelFilter.Group(0)))
        assertEquals(4, names("").size)
    }

    @Test
    fun parsesTheAddressShownOnTheTv() {
        assertEquals("192.168.1.50" to 8080, Logic.parseAddress("192.168.1.50"))
        assertEquals("192.168.1.50" to 9000, Logic.parseAddress(" 192.168.1.50:9000 "))
        assertEquals("192.168.1.50" to 8080, Logic.parseAddress("http://192.168.1.50:8080/"))
        assertEquals("tele.local" to 8080, Logic.parseAddress("tele.local"))
        assertNull(Logic.parseAddress("  "))
    }

    @Test
    fun findsWebLinksInSharedText() {
        assertTrue(Logic.isWebLink("https://example.com/live.m3u8"))
        assertFalse(Logic.isWebLink("file:///etc/passwd"))
        assertFalse(Logic.isWebLink("http://"))
        assertEquals("https://example.com/a.m3u", Logic.firstLink("My list: https://example.com/a.m3u."))
        assertNull(Logic.firstLink("no links here"))
    }

    @Test
    fun initialForTheLogoPlaceholder() {
        assertEquals("T", Logic.initial("telecinco"))
        assertEquals("1", Logic.initial("  1TV"))
        assertEquals("·", Logic.initial("★★"))
    }
}
