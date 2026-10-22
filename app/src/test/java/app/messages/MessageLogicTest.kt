package app.messages

import app.lantext.sms.echoStoredTimestamp
import app.lantext.sms.orderEchoes
import app.lantext.sms.parseStoredMessageId
import app.messages.data.ChatMessage
import app.messages.data.Persisted
import app.messages.data.MessageLinks
import app.messages.data.RibbonItem
import app.messages.data.VerificationCodes
import app.messages.data.moveRibbonItem
import app.messages.data.resolvedRibbon
import app.messages.data.ribbonTopCount
import app.messages.sms.KlipyGifs
import app.messages.data.SimLine
import app.messages.data.chosenSub
import app.messages.data.composeStartText
import app.messages.data.formatThreadTitle
import app.messages.data.messageCaption
import app.messages.data.messageParty
import app.messages.data.preferredSub
import app.messages.data.simCaption
import app.messages.data.simDetail
import app.messages.data.unreadBadge
import app.messages.data.formatTimestamp
import app.messages.data.initials
import app.messages.data.keywordsFrom
import app.messages.data.matchesBlockedKeyword
import app.messages.data.sameRecipients
import app.messages.sms.MmsNotificationParser
import app.messages.sms.MmsRetrieveParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MessageLogicTest {
    @Test
    fun parsesNotificationPdu() {
        val pdu = notificationPdu()
        val parsed = MmsNotificationParser.parse(pdu)
        assertEquals("abc", parsed?.transactionId)
        assertEquals("http://mmsc/x", parsed?.contentLocation)
        assertEquals("+15551212", parsed?.from)
        assertEquals(4096L, parsed?.size)
    }

    @Test
    fun rejectsEmptyPdu() {
        assertNull(MmsNotificationParser.parse(byteArrayOf()))
        assertNull(MmsNotificationParser.parse(null))
    }

    @Test
    fun klipyPicksGifUnderMmsCap() {
        val hits = KlipyGifs.hitsFrom(
            """
            {"result":true,"data":{"data":[
              {"id":99,"type":"gif","file":{
                "hd":{"gif":{"url":"https://static.klipy.com/a.gif","size":4000000}},
                "sm":{"gif":{"url":"https://static.klipy.com/b.gif","size":80000}},
                "xs":{"gif":{"url":"https://static.klipy.co/c.gif","size":20000}}
              }},
              {"id":100,"type":"ad","file":{}},
              {"id":101,"type":"gif","file":{"xs":{"gif":{"url":"http://static.klipy.com/x.gif","size":1000}}}},
              {"id":102,"type":"gif","file":{"xs":{"gif":{"url":"https://evil.example/x.gif","size":1000}}}},
              {"id":103,"type":"gif","file":{"hd":{"gif":{"url":"https://static.klipy.com/big.gif","size":900000}}}}
            ]}}
            """.trimIndent(),
            280_000,
        )
        assertEquals(1, hits.size)
        assertEquals("99", hits[0].id)
        assertEquals("https://static.klipy.com/b.gif", hits[0].sendUrl)
        assertEquals("https://static.klipy.co/c.gif", hits[0].previewUrl)
        assertEquals(80_000, hits[0].bytes)
        assertFalse(KlipyGifs.isAllowed("http://static.klipy.com/a.gif"))
        assertFalse(KlipyGifs.isAllowed("https://evil.example/a.gif"))
        assertTrue(KlipyGifs.isAllowed("https://static.klipy.com/a.gif"))
        try {
            KlipyGifs.search("bad/key", "hi", 1000)
            throw AssertionError("slash key")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun selfSendEchoSortsAfterTheOutgoingCopy() {
        val sent = sample(id = 1, body = "same", at = 1_000_000, incoming = false)
        val echo = sample(id = 2, body = "same", at = 940_000, incoming = true)
        val older = sample(id = 3, body = "other", at = 900_000, incoming = true)
        val ordered = orderEchoes(
            listOf(echo, sent, older),
            { it.key },
            { it.threadId.toString() },
            { it.body },
            { it.timestamp },
            { it.incoming },
        )
        assertEquals(listOf("sms-3", "sms-1", "sms-2"), ordered.map { it.key })
        assertEquals(1_000_001L, echoStoredTimestamp(940_000, listOf(1_000_000)))
        assertEquals(940_000L, echoStoredTimestamp(940_000, listOf(1_500_000)))
    }

    private fun sample(id: Long, body: String, at: Long, incoming: Boolean) = ChatMessage(
        smsId = id,
        mmsId = null,
        threadId = 7,
        address = "0000999",
        senderName = "",
        body = body,
        timestamp = at,
        incoming = incoming,
        status = "",
        subId = -1,
        attachments = emptyList(),
    )

    @Test
    fun keywordFilterIgnoresBlanks() {
        assertFalse(matchesBlockedKeyword("hello", listOf(" ", "")))
        assertTrue(matchesBlockedKeyword("Win a prize", listOf("prize")))
        assertEquals(listOf("prize", "loan"), keywordsFrom(" prize \n\nloan\nprize "))
    }

    @Test
    fun titlesAndTimes() {
        assertEquals("Ada, Bo", formatThreadTitle(listOf("Ada", "Bo")))
        assertEquals("Ada +2", formatThreadTitle(listOf("Ada", "Bo", "Cy")))
        assertEquals("AL", initials("Ada Lovelace"))
        assertEquals("", unreadBadge(0))
        assertEquals("3", unreadBadge(3))
        assertEquals("99+", unreadBadge(120))
        assertEquals("#", initials("0000999"))
        val zone = ZoneId.of("UTC")
        val noon = LocalDate.of(2026, 10, 20).atTime(15, 5).atZone(zone).toInstant().toEpochMilli()
        val morning = LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(formatTimestamp(morning, noon, zone).contains("AM"))
    }

    @Test
    fun messageIdsKeepKind() {
        assertEquals(false, parseStoredMessageId("sms-12")?.mms)
        assertEquals(12L, parseStoredMessageId("sms-12")?.id)
        assertEquals(true, parseStoredMessageId("mms-4")?.mms)
        assertNull(parseStoredMessageId("sms-0"))
        assertNull(parseStoredMessageId("note-3"))
    }

    @Test
    fun verificationCodeIsTheTokenNextToTheKeyword() {
        assertEquals("482913", VerificationCodes.find("Your bank login code is 482913"))
        assertEquals("123456", VerificationCodes.find("123456 is your verification code."))
        assertEquals("123456", VerificationCodes.find("G-123456 is your Google verification code."))
        assertEquals("482913", VerificationCodes.find("Your code is 482-913."))
        assertEquals("K7H2P9", VerificationCodes.find("Your login code is K7H2P9"))
        assertEquals("111111", VerificationCodes.find("Your login code is 111111. Backup code 222222."))
        assertEquals(
            "482913",
            VerificationCodes.find("Enter code 482913 to log in. Call 1-800-555-0199 if needed."),
        )
        assertEquals(
            "482913",
            VerificationCodes.find(
                "Provident verification code. Do not share this code with anyone:482913 Reply HELP or call 1-800-555-0199.",
            ),
        )
        assertNull(VerificationCodes.find("Your verification code call is at 800-632-4600 today"))
        assertEquals("2026", VerificationCodes.find("Your code is 2026"))
        assertNull(VerificationCodes.find("Order 482913 has shipped"))
        assertNull(VerificationCodes.find("Meet me at 2024 Main Street"))
        assertNull(VerificationCodes.find("For your security this was sent in 2026"))
        assertNull(VerificationCodes.find("Your security deposit is $1234."))
        assertNull(VerificationCodes.find("For your security call 800-555-0199"))
        assertNull(VerificationCodes.find("Your verification expires on 10/21/2026"))
        assertNull(VerificationCodes.find("code 123"))
        assertNull(VerificationCodes.find("verification 123456789"))
        assertNull(VerificationCodes.find("barcode 482913"))
    }

    @Test
    fun ribbonOrderKeepsKnownActions() {
        val fresh = resolvedRibbon(emptyList())
        assertEquals(listOf("read", "unread", "delete", "block", "archive", "pin"), fresh.map { it.id })
        assertTrue(fresh.all { it.shown })
        val saved = listOf(RibbonItem("pin", false), RibbonItem("read"), RibbonItem("nope"))
        val resolved = resolvedRibbon(saved)
        assertEquals(listOf("pin", "read", "unread", "delete", "block", "archive"), resolved.map { it.id })
        assertFalse(resolved.first().shown)
        assertEquals("read", moveRibbonItem(resolved, 0, 1).first().id)
        assertEquals(resolved, moveRibbonItem(resolved, 0, -1))
        assertEquals(2, ribbonTopCount(2))
        assertEquals(1, ribbonTopCount(0))
        assertEquals(5, ribbonTopCount(9))
    }

    @Test
    fun newInstallMatchesThePhoneLayout() {
        val fresh = Persisted()
        assertEquals("ocean", fresh.palette)
        assertEquals("cyan", fresh.launcherIcon)
        assertEquals("system", fresh.theme)
        assertFalse(fresh.dynamicColor)
        assertTrue(fresh.searchAtBottom)
        assertTrue(fresh.stripedInbox)
        assertEquals("delete", fresh.swipeStart)
        assertEquals("unread", fresh.swipeEnd)
        assertFalse(fresh.showSimIndicator)
        assertFalse(fresh.detailDirection)
        assertFalse(fresh.detailSim)
        assertEquals(68, fresh.footerHeight)
        assertEquals(listOf("read", "unread", "delete", "block", "archive", "pin"), fresh.ribbon.map { it.id })
        assertFalse(fresh.ribbon.first { it.id == "archive" }.shown)
        assertEquals(listOf("filter", "search", "settings", "computer"), fresh.footer.map { it.id })
        assertTrue(fresh.footer.all { it.shown })
    }

    @Test
    fun linksAreClickableTargets() {
        val hits = MessageLinks.find("See https://example.com/a. and (www.example.org/b).")
        assertEquals(2, hits.size)
        assertEquals("https://example.com/a", hits[0].url)
        assertEquals("www.example.org/b", hits[1].url)
        assertEquals("https://www.example.org/b", MessageLinks.href(hits[1].url))
        assertEquals("example.org", MessageLinks.host(hits[1].url))
        assertTrue(MessageLinks.find("Write a@example.com please").isEmpty())
    }

    @Test
    fun readsRetrievedPicture() {
        val parsed = MmsRetrieveParser.parse(retrievePdu())
        assertEquals("+15551212", parsed?.from)
        assertEquals(1, parsed?.parts?.size)
        assertEquals("image/jpeg", parsed?.parts?.first()?.mime)
        assertEquals("a.jpg", parsed?.parts?.first()?.name)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertTrue(parsed!!.parts.first().bytes.contentEquals(jpeg))
        assertNull(MmsRetrieveParser.parse(byteArrayOf(0x8C.toByte(), 0x82.toByte())))
    }

    @Test
    fun recipientMatchIgnoresFormatting() {
        assertTrue(sameRecipients(listOf("+1 (555) 0100"), listOf("15550100")))
        assertTrue(sameRecipients(listOf("5550101", "5550100"), listOf("5550100", "5550101")))
        assertFalse(sameRecipients(listOf("5550100"), listOf("5550101")))
    }

    @Test
    fun captionsSimsAndBlankCompose() {
        val sims = listOf(SimLine(7, 0, "Example"), SimLine(8, 1, ""), SimLine(9, 2, "5551234567"))
        assertEquals(7, preferredSub(7, 3))
        assertEquals(3, preferredSub(-1, 3))
        assertEquals(7, chosenSub(sims, 7))
        assertEquals(8, chosenSub(sims, 8))
        assertEquals(7, chosenSub(sims, 99))
        assertNull(chosenSub(emptyList(), 7))
        assertEquals("SIM 1", simCaption(7, sims))
        assertEquals("SIM", simCaption(4, sims))
        assertEquals("", simCaption(-1, sims))
        assertEquals("SIM 1 Example", simDetail(7, sims))
        assertEquals("SIM 2", simDetail(8, sims))
        assertEquals("SIM 3", simDetail(9, sims))
        assertEquals("You", messageParty(false, "Pat", "555"))
        assertEquals("Ada", messageParty(true, "Ada", "555"))
        assertEquals("555", messageParty(true, "555", "555"))
        assertEquals(
            "Received · Ada · 3:00 PM · SMS · SIM 1 Example",
            messageCaption(true, "Ada", "3:00 PM", false, "SIM 1 Example", true, true, true, true, true),
        )
        assertEquals("Sent · MMS", messageCaption(false, "", "", true, "", true, true, true, true, true))
        assertEquals("", messageCaption(true, "Ada", "3:00 PM", false, "SIM", false, false, false, false, false))
        assertEquals("", composeStartText("", emptyList(), "https://maps.google.com/?q=1,2"))
        assertEquals("Hello", composeStartText("Hello", emptyList(), "https://maps.google.com/?q=1,2"))
        assertEquals("saved", composeStartText("", listOf("1"), "saved"))
    }

    private fun notificationPdu(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x8C)
        out.write(0x82)
        out.write(0x98)
        out.write("abc".toByteArray())
        out.write(0)
        out.write(0x8D)
        out.write(0x92)
        val address = "+15551212".toByteArray() + byteArrayOf(0)
        val from = byteArrayOf(0x80.toByte()) + address
        out.write(0x89)
        out.write(from.size)
        out.write(from)
        out.write(0x8A)
        out.write(0x80)
        out.write(0x8E)
        out.write(0x02)
        out.write(0x10)
        out.write(0x00)
        out.write(0x88)
        out.write(0x04)
        out.write(0x81)
        out.write(0x02)
        out.write(0x0E)
        out.write(0x10)
        out.write(0x96)
        out.write("Hi".toByteArray())
        out.write(0)
        out.write(0x83)
        out.write("http://mmsc/x".toByteArray())
        out.write(0)
        return out.toByteArray()
    }

    private fun retrievePdu(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w(value: Int) = out.write(value)
        fun bytes(value: ByteArray) = out.write(value)
        fun text(value: String) = value.toByteArray() + byteArrayOf(0)
        w(0x8C)
        w(0x84)
        w(0x8D)
        w(0x92)
        w(0x98)
        bytes(text("tid"))
        val from = byteArrayOf(0x80.toByte()) + text("+15551212")
        w(0x89)
        w(from.size)
        bytes(from)
        val contentType = byteArrayOf(0xB3.toByte(), 0x8A.toByte()) + text("<smil>")
        w(0x84)
        w(contentType.size)
        bytes(contentType)
        val name = text("a.jpg")
        val header = byteArrayOf((name.size + 2).toByte(), 0x9E.toByte(), 0x85.toByte()) + name + byteArrayOf(0x8E.toByte()) + name
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        w(1)
        w(header.size)
        w(jpeg.size)
        bytes(header)
        bytes(jpeg)
        return out.toByteArray()
    }
}
