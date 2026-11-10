package app.messages

import app.lantext.sms.SELF_ECHO_WINDOW_MS
import app.lantext.sms.echoStoredTimestamp
import app.lantext.sms.orderEchoes
import app.lantext.sms.parseStoredMessageId
import app.messages.data.ChatMessage
import app.messages.data.PartRef
import app.messages.data.Conversation
import app.messages.data.Persisted
import app.messages.data.InboxListEntry
import app.messages.data.TrashedConversation
import app.messages.data.TrashedThread
import app.messages.data.groupInbox
import app.messages.data.inboxEntries
import app.messages.data.inboxGroupMode
import app.messages.data.receivedBucket
import app.messages.data.trashSections
import java.time.temporal.WeekFields
import app.messages.data.coerceTrashDays
import app.messages.data.reconcileTrash
import app.messages.data.trashDueForDeletion
import app.messages.data.resolvedFooter
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
import app.messages.data.formatMessageStamp
import app.messages.data.formatTimestamp
import app.messages.data.schedulePickerSeed
import app.messages.data.initials
import app.messages.data.keywordsFrom
import app.messages.data.matchesBlockedKeyword
import app.messages.data.sameRecipients
import app.messages.sms.MmsNotificationParser
import app.messages.sms.SendIntents
import app.messages.sms.MmsRetrieveParser
import app.lantext.brandProductText
import app.lantext.data.pairedDeviceName
import app.lantext.data.webAccessEnabled
import app.lantext.net.TlsCertificateStore
import app.lantext.util.PhoneNumbers
import app.messages.data.footerButtonDp
import app.messages.data.footerGlyphDp
import app.messages.data.swipeCommitPx
import app.messages.data.swipeReadUnreadLabel
import app.lantext.sms.latestMessageIsMms
import app.messages.data.swipeRevealReady
import app.messages.ui.reorderSlots
import app.messages.ui.ThemeChoices
import app.messages.ui.contrastRatio
import app.messages.ui.mailCanvas
import app.messages.ui.mailCard
import app.messages.ui.mailInk
import app.messages.ui.mailSearch
import androidx.compose.ui.graphics.luminance
import app.messages.ui.COMPOSER_PANEL_MAX_FRACTION
import app.messages.ui.EmojiCatalog
import app.messages.ui.SETTINGS_CARD_MAX
import app.messages.ui.SETTINGS_CARD_MIN
import app.messages.ui.composerPanelHeightDp
import app.messages.ui.emojiResults
import app.messages.ui.settingsCardFraction
import app.messages.sms.inboxSnippet
import app.messages.sms.placeGroup
import app.messages.sms.reactionLayout
import app.messages.sms.tapbackBody
import app.messages.sms.tapbackQuote
import app.messages.sms.tapbackSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
    fun rejectsANotificationLengthThatOverflows() {
        val pdu = byteArrayOf(
            0x8C.toByte(),
            0x82.toByte(),
            0x88.toByte(),
            0x1F,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
        )
        assertNull(MmsNotificationParser.parse(pdu))
        assertNull(MmsRetrieveParser.parse(byteArrayOf(0x96.toByte(), 0x1F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
    }

    @Test
    fun shareAttachmentsStayOutsideThisApp() {
        val pkg = "app.crome1394.blurttext"
        assertTrue(SendIntents.shareAttachmentAllowed("content", "media", pkg))
        assertFalse(SendIntents.shareAttachmentAllowed("file", "ignored", pkg))
        assertFalse(SendIntents.shareAttachmentAllowed("content", "$pkg.files", pkg))
        assertFalse(SendIntents.shareAttachmentAllowed("content", "$pkg.MmsFileProvider", pkg))
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
        val at = LocalDate.of(2026, 10, 22).atTime(15, 4, 7).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 10, 22).atTime(16, 0, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("3:04:07 PM", formatMessageStamp(at, now, zone))
        val yesterday = LocalDate.of(2026, 10, 21).atTime(15, 4, 7).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Yesterday 3:04:07 PM", formatMessageStamp(yesterday, now, zone))
        val older = LocalDate.of(2025, 10, 22).atTime(15, 4, 7).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Oct 22, 2025, 3:04:07 PM", formatMessageStamp(older, now, zone))
        val earlierThisYear = LocalDate.of(2026, 3, 2).atTime(15, 4, 7).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Mar 2, 3:04:07 PM", formatMessageStamp(earlierThisYear, now, zone))
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val evening = LocalDate.of(2026, 10, 22).atTime(20, 30, 7).atZone(losAngeles).toInstant().toEpochMilli()
        val eveningSeed = schedulePickerSeed(evening, losAngeles)
        val oct22Utc = LocalDate.of(2026, 10, 22).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(oct22Utc, eveningSeed.dateUtcMillis)
        assertEquals(20, eveningSeed.hour)
        assertEquals(30, eveningSeed.minute)
        val tokyo = ZoneId.of("Asia/Tokyo")
        val afterMidnight = LocalDate.of(2026, 10, 23).atTime(1, 5).atZone(tokyo).toInstant().toEpochMilli()
        val tokyoSeed = schedulePickerSeed(afterMidnight, tokyo)
        val oct23Utc = LocalDate.of(2026, 10, 23).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(oct23Utc, tokyoSeed.dateUtcMillis)
        assertEquals(1, tokyoSeed.hour)
        assertEquals(5, tokyoSeed.minute)
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
        assertNull(VerificationCodes.find("Your verification code call is at 800-555-0199 today"))
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
        assertTrue(fresh.trash.isEmpty())
        assertFalse(fresh.autoTrashEnabled)
        assertEquals(30, fresh.autoTrashDays)
        assertFalse(fresh.autoDeleteTrashEnabled)
        assertEquals(30, fresh.autoDeleteTrashDays)
        assertEquals("none", fresh.inboxGroup)
    }

    @Test
    fun footerAddsTrashHiddenUntilTurnedOn() {
        val resolved = resolvedFooter(Persisted().footer)
        assertEquals(listOf("filter", "search", "settings", "computer", "trash"), resolved.map { it.id })
        assertFalse(resolved.first { it.id == "trash" }.shown)
        assertTrue(resolved.filter { it.id != "trash" }.all { it.shown })
        val shown = resolvedFooter(Persisted().footer + RibbonItem("trash", shown = true))
        assertTrue(shown.first { it.id == "trash" }.shown)
    }

    @Test
    fun oldConversationsMoveToTrashAndANewerMessageRestoresThem() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val old = sampleConversation(1, now - 40 * day)
        val recent = sampleConversation(2, now - 2 * day)
        val pinned = sampleConversation(3, now - 90 * day)
        val open = sampleConversation(6, now - 90 * day)
        val replied = sampleConversation(4, now)
        val waiting = sampleConversation(5, now - 10 * day)
        val trash = listOf(TrashedThread(4, now - day), TrashedThread(5, now - day), TrashedThread(9, now - day))
        val conversations = listOf(old, recent, pinned, open, replied, waiting)
        val next = reconcileTrash(conversations, trash, autoEnabled = true, days = 30, now = now, openThreadId = 6, pinned = setOf(3))
        assertTrue(next.any { it.threadId == 1L })
        assertFalse(next.any { it.threadId == 2L })
        assertFalse(next.any { it.threadId == 3L })
        assertFalse(next.any { it.threadId == 6L })
        assertFalse(next.any { it.threadId == 4L })
        assertTrue(next.any { it.threadId == 5L })
        assertTrue(next.any { it.threadId == 9L })
        val off = reconcileTrash(conversations, trash, autoEnabled = false, days = 30, now = now, openThreadId = -1, pinned = emptySet())
        assertFalse(off.any { it.threadId == 1L })
        assertFalse(off.any { it.threadId == 4L })
        assertEquals(trash, reconcileTrash(emptyList(), trash, autoEnabled = true, days = 30, now = now, openThreadId = -1, pinned = emptySet()))
        assertEquals(1, coerceTrashDays(0))
        assertEquals(30, coerceTrashDays(30))
        assertEquals(3650, coerceTrashDays(9_999))
    }

    @Test
    fun trashIsDeletedOnlyAfterItHasWaited() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val trash = listOf(
            TrashedThread(1, now - 40 * day),
            TrashedThread(2, now - 2 * day),
            TrashedThread(3, now - 30 * day),
        )
        assertTrue(trashDueForDeletion(trash, enabled = false, days = 30, now = now).isEmpty())
        val due = trashDueForDeletion(trash, enabled = true, days = 30, now = now, openThreadId = 1)
        assertFalse(due.contains(1L))
        assertFalse(due.contains(2L))
        assertTrue(due.contains(3L))
    }

    @Test
    fun swipeWaitsBeforeItReveals() {
        val slop = 8f
        assertFalse(swipeRevealReady(elapsedMs = 0, travelX = 80f, travelY = 4f, slop = slop))
        assertFalse(swipeRevealReady(elapsedMs = 99, travelX = 80f, travelY = 4f, slop = slop))
        assertTrue(swipeRevealReady(elapsedMs = 100, travelX = 80f, travelY = 4f, slop = slop))
        assertFalse(swipeRevealReady(elapsedMs = 200, travelX = 20f, travelY = 80f, slop = slop))
        assertFalse(swipeRevealReady(elapsedMs = 200, travelX = 10f, travelY = 4f, slop = slop))
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
        assertTrue(parsed?.to.orEmpty().isEmpty())
        assertTrue(parsed?.cc.orEmpty().isEmpty())
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertTrue(parsed!!.parts.first().bytes.contentEquals(jpeg))
        assertNull(MmsRetrieveParser.parse(byteArrayOf(0x8C.toByte(), 0x82.toByte())))
    }

    @Test
    fun readsRetrievedTextInItsCharset() {
        val parsed = MmsRetrieveParser.parse(textRetrievePdu())
        val part = parsed?.parts?.single()
        assertEquals("text/plain", part?.mime)
        assertEquals(4, part?.charset)
        assertEquals("caf\u00e9", part?.text())
    }

    @Test
    fun groupRetrieveKeepsEveryRecipient() {
        val parsed = MmsRetrieveParser.parse(groupRetrievePdu())
        assertEquals("+15551212", parsed?.from)
        assertEquals(listOf("+15550001", "+15550002"), parsed?.to)
        assertEquals(listOf("pat@example.com"), parsed?.cc)
        assertEquals(1, parsed?.parts?.size)
    }

    @Test
    fun groupPlacementDropsThisPhoneAndKeepsTheOtherPeople() {
        val own = listOf("+1 555-000-0000")
        val group = placeGroup("+15551212", listOf("+15550001", "+15550000000"), emptyList(), own)
        assertEquals(listOf("+15551212", "+15550001"), group.participants)
        val oneToOne = placeGroup("+15551212", listOf("+15550000000"), emptyList(), own)
        assertEquals(listOf("+15551212"), oneToOne.participants)
        val unknownOwn = placeGroup("+15551212", listOf("+15550000000"), emptyList(), emptyList())
        assertEquals(listOf("+15551212"), unknownOwn.participants)
        val groupWithoutOwnNumber = placeGroup(
            "+15551212",
            listOf("+15550001", "+15550000000"),
            emptyList(),
            emptyList(),
        )
        assertEquals(listOf("+15551212", "+15550001", "+15550000000"), groupWithoutOwnNumber.participants)
        val carrierOmittedSelf = placeGroup("+15551212", listOf("5550001"), listOf("pat@example.com"), own)
        assertEquals(listOf("+15551212", "5550001", "pat@example.com"), carrierOmittedSelf.participants)
    }

    @Test
    fun inboxPreviewUsesTheNewestTextWhenAPictureIsOlder() {
        assertEquals("See you there", inboxSnippet("", true, "See you there", "Picture"))
        assertEquals("See you there", inboxSnippet("Picture", true, "See you there", ""))
        assertEquals("Picture", inboxSnippet("", true, "", "Picture"))
        assertEquals("hello", inboxSnippet("hello", false, "", ""))
        assertEquals("latest", inboxSnippet("", false, "latest", ""))
        assertEquals("👍 Liked a message", inboxSnippet("", false, "Liked \u201chi there friend\u201d", ""))
    }

    @Test
    fun likeLandsOnTheOriginalMessage() {
        val original = sample(
            1,
            "Sample note for the thread. The sample app missed the update.",
            1_000,
            false,
        )
        val like = sample(2, "Liked \u201c\u2026missed the update.\u201d", 2_000, true).copy(senderName = "Alex Example")
        val laid = reactionLayout(listOf(original, like))
        assertTrue(like.key in laid.hiddenKeys)
        val mark = laid.byKey[original.key]!!.single()
        assertEquals("\uD83D\uDC4D", mark.symbol)
        assertEquals(listOf("Alex Example"), mark.names)
        val removed = sample(3, "Removed a like from \u201c\u2026missed the update.\u201d", 3_000, true)
            .copy(senderName = "Alex Example")
        val cleared = reactionLayout(listOf(original, like, removed))
        assertTrue(laidOutEmpty(cleared, original.key))
        assertTrue(like.key in cleared.hiddenKeys)
        assertTrue(removed.key in cleared.hiddenKeys)
        val topic = sample(4, "Sample line stays on the left while another sample line stays on the right today.", 4_000, false)
        val reacted = sample(5, "Reacted \uD83D\uDE22 to \u201cSample line stays on the left while another sample line stays on the right...\u201d", 5_000, true)
            .copy(senderName = "Alex Example")
        val emoji = reactionLayout(listOf(topic, reacted))
        assertTrue(reacted.key in emoji.hiddenKeys)
        assertEquals("\uD83D\uDE22", emoji.byKey[topic.key]!!.single().symbol)
    }

    @Test
    fun sentTapbackLandsOnThePillAsYou() {
        val love = tapbackSymbols.first { it.label == "Love" }
        val original = sample(1, "Will do =)", 1_000, true).copy(senderName = "Alex Example")
        val body = tapbackBody(original, love, remove = false)
        assertEquals("Loved \u201cWill do =)\u201d", body)
        val sent = sample(2, body!!, 2_000, false).copy(senderName = "Alex Example", address = "+15555550100")
        val laid = reactionLayout(listOf(original, sent))
        assertTrue(sent.key in laid.hiddenKeys)
        val mark = laid.byKey[original.key]!!.single()
        assertEquals(love.symbol, mark.symbol)
        assertEquals(listOf("You"), mark.names)
        val removal = tapbackBody(original, love, remove = true)
        assertEquals("Removed a heart from \u201cWill do =)\u201d", removal)
        val cleared = reactionLayout(listOf(original, sent, sample(3, removal!!, 3_000, false)))
        assertTrue(laidOutEmpty(cleared, original.key))
        val image = sample(4, "", 4_000, true).copy(attachments = listOf(PartRef(9, "image/jpeg")))
        assertEquals("Liked an image", tapbackBody(image, tapbackSymbols.first(), false))
        assertNull(tapbackBody(sample(5, "   ", 5_000, true), tapbackSymbols.first(), false))
        assertEquals("He said ok today", tapbackQuote("He said \"ok\" today"))
    }

    @Test
    fun longTapbackQuoteStillLandsOnTheMessage() {
        val body = "A".repeat(90)
        val quote = tapbackQuote(body)
        assertEquals("A".repeat(80) + "\u2026", quote)
        val message = sample(1, body, 1_000, true)
        val sentBody = tapbackBody(message, tapbackSymbols.first(), false)
        assertEquals("Liked \u201c${"A".repeat(80)}\u2026\u201d", sentBody)
        val sent = sample(2, sentBody!!, 2_000, false)
        val laid = reactionLayout(listOf(message, sent))
        assertTrue(sent.key in laid.hiddenKeys)
        assertEquals(tapbackSymbols.first().symbol, laid.byKey[message.key]!!.single().symbol)
        assertEquals(listOf("You"), laid.byKey[message.key]!!.single().names)
    }

    @Test
    fun echoedLoveCountsOnceAsYou() {
        val original = sample(1, "On my way", 1_000, true).copy(senderName = "Alex Example")
        val body = "Loved \u201cOn my way\u201d"
        val sent = sample(2, body, 2_000, false)
        val echo = sample(3, body, 2_001, true).copy(senderName = "Alex Example")
        val laid = reactionLayout(listOf(original, sent, echo))
        assertTrue(sent.key in laid.hiddenKeys)
        assertTrue(echo.key in laid.hiddenKeys)
        val mark = laid.byKey[original.key]!!.single()
        assertEquals("\u2764\uFE0F", mark.symbol)
        assertEquals(listOf("You"), mark.names)
        val early = sample(4, body, 2_000 - 1_000, true).copy(senderName = "Alex Example")
        val earlyLaid = reactionLayout(listOf(original, early, sent))
        assertEquals(listOf("You"), earlyLaid.byKey[original.key]!!.single().names)
        val theirs = sample(5, body, 2_000 + SELF_ECHO_WINDOW_MS + 1, true).copy(senderName = "Alex Example")
        val both = reactionLayout(listOf(original, sent, theirs))
        assertEquals(listOf("You", "Alex Example"), both.byKey[original.key]!!.single().names)
    }

    @Test
    fun shortCodeMatchesTheContactWithOrWithoutACountryCode() {
        assertTrue(PhoneNumbers.lookupKeys("49447", "US").contains("49447"))
        assertTrue(PhoneNumbers.lookupKeys("+1 49447", "US").contains("49447"))
        assertTrue(PhoneNumbers.lookupKeys("1-49447", "US").contains("49447"))
        assertTrue(PhoneNumbers.lookupKeys("26499", "US").contains("26499"))
        assertTrue(PhoneNumbers.lookupKeys("+1 555-555-0100", "US").none { it.length in 3..6 })
        assertTrue(PhoneNumbers.phoneLookupForms("49447", "US").contains("+149447"))
    }

    @Test
    fun swipeCommitsAtAQuarterOfTheRowAndFooterIconsGrow() {
        assertEquals(250f, swipeCommitPx(1000f))
        assertFalse(249f >= swipeCommitPx(1000f))
        assertEquals("Mark as unread", swipeReadUnreadLabel(false))
        assertEquals("Mark as read", swipeReadUnreadLabel(true))
        assertFalse(latestMessageIsMms(2_000L, null))
        assertTrue(latestMessageIsMms(null, 2_000L))
        assertTrue(latestMessageIsMms(2_000L, 2_000L))
        assertTrue(latestMessageIsMms(1_000L, 2_000L))
        assertFalse(latestMessageIsMms(3_000L, 2_000L))
        assertTrue(footerButtonDp(96) > footerButtonDp(68))
        assertTrue(footerGlyphDp(96) > footerGlyphDp(48))
        assertEquals("BlurtText", brandProductText("LanText", "BlurtText"))
        assertEquals("Open BlurtText", brandProductText("Open LanMessaging", "BlurtText"))
        assertEquals("blurttext.lan", brandProductText("blurttext.lan", "BlurtText"))
        assertFalse(webAccessEnabled(false, 1))
        assertTrue(webAccessEnabled(true, 0))
        assertFalse(webAccessEnabled(null, 0))
        assertTrue(webAccessEnabled(null, 1))
        assertEquals("LanText", brandProductText("LanText", "LanText"))
        assertEquals("Device", pairedDeviceName("  "))
        assertEquals("Kitchen laptop", pairedDeviceName("Kitchen laptop"))
        assertEquals(
            "Chrome on Linux",
            pairedDeviceName("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"),
        )
        assertEquals(
            "Firefox on Windows",
            pairedDeviceName("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"),
        )
        assertEquals(
            "Safari on Mac",
            pairedDeviceName("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"),
        )
        assertEquals(
            "Chrome on Android",
            pairedDeviceName("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"),
        )
        assertEquals(
            "Edge on Windows",
            pairedDeviceName("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"),
        )
        assertEquals(
            "Chrome on ChromeOS",
            pairedDeviceName("Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"),
        )
    }

    @Test
    fun settingsCardGrowsUpAndShrinksDown() {
        assertEquals(0.5f, settingsCardFraction(0.6f, 100f, 1000f), 0.001f)
        assertEquals(0.7f, settingsCardFraction(0.6f, -100f, 1000f), 0.001f)
        assertEquals(SETTINGS_CARD_MIN, settingsCardFraction(0.4f, 10_000f, 1000f), 0.001f)
        assertEquals(SETTINGS_CARD_MAX, settingsCardFraction(0.9f, -10_000f, 1000f), 0.001f)
        assertEquals(0.6f, settingsCardFraction(0.6f, 40f, 0f), 0.001f)
    }

    @Test
    fun composerPanelGrowsUpAndShrinksDown() {
        assertEquals(0.62f, COMPOSER_PANEL_MAX_FRACTION, 0.001f)
        assertEquals(320f, composerPanelHeightDp(340f, 40f, 2f, 280f, 600f), 0.001f)
        assertEquals(380f, composerPanelHeightDp(340f, -80f, 2f, 280f, 600f), 0.001f)
        assertEquals(280f, composerPanelHeightDp(300f, 10_000f, 2f, 280f, 600f), 0.001f)
        assertEquals(600f, composerPanelHeightDp(500f, -10_000f, 2f, 280f, 600f), 0.001f)
        assertEquals(340f, composerPanelHeightDp(340f, 40f, 0f, 280f, 600f), 0.001f)
        assertEquals(280f, composerPanelHeightDp(100f, 0f, 2f, 400f, 280f), 0.001f)
    }

    @Test
    fun emojiSearchFindsNamesAndKeepsABlankQueryOnTheCategory() {
        assertEquals(EmojiCatalog[0].glyphs, emojiResults("", 0))
        assertEquals(EmojiCatalog[3].glyphs, emojiResults("  ", 3))
        assertEquals(EmojiCatalog[0].glyphs, emojiResults("", 99))
        assertFalse(emojiResults("", 0).contains("🐶"))
        val dogs = emojiResults("dog", 0)
        assertTrue(dogs.contains("🐶"))
        assertFalse(dogs.contains("😀"))
        assertEquals(dogs, emojiResults("DOG", 4))
        val hearts = emojiResults("heart", 0)
        assertTrue(hearts.contains("❤️"))
        assertTrue(hearts.contains("😍"))
        assertFalse(hearts.contains("🐶"))
        assertTrue(emojiResults("Smileys", 2).contains("😀"))
        assertFalse(emojiResults("Smileys", 2).contains("🐶"))
        assertTrue(emojiResults("zzzznotemoji", 0).isEmpty())
    }

    @Test
    fun certificateDnsNameIsReadWhenTheSanTypeIsAJavaInteger() {
        val rows = listOf(
            listOf(Integer.valueOf(2), "blurttext.lan."),
            listOf(Integer.valueOf(7), byteArrayOf(10, 74, 10, 109)),
            listOf(Integer.valueOf(2), "localhost".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(
            listOf("blurttext.lan", "localhost"),
            TlsCertificateStore.dnsNamesFromSan(rows),
        )
    }

    private fun sampleConversation(id: Long, timestamp: Long) = Conversation(
        threadId = id,
        addresses = emptyList(),
        title = "",
        snippet = "",
        timestamp = timestamp,
        unread = 0,
        archived = false,
        pinned = false,
        draft = "",
        photoUri = null,
    )

    private fun laidOutEmpty(layout: app.messages.sms.ReactionLayout, key: String): Boolean =
        layout.byKey[key].isNullOrEmpty()

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

    @Test
    fun mailPageUsesTheSelectedPalette() {
        ThemeChoices.forEach { choice ->
            val darkCard = choice.dark.mailCard()
            val darkPage = choice.dark.mailCanvas()
            val darkSearch = choice.dark.mailSearch()
            assertTrue(darkPage.luminance() > darkCard.luminance())
            assertTrue(darkSearch.luminance() > darkPage.luminance())
            val cardSpread = channelSpread(darkCard.red, darkCard.green, darkCard.blue)
            val pageSpread = channelSpread(darkPage.red, darkPage.green, darkPage.blue)
            if (cardSpread > 0.02f) assertTrue(pageSpread > cardSpread * 0.5f)
            val lightCard = choice.light.mailCard()
            val lightPage = choice.light.mailCanvas()
            assertTrue(lightPage.luminance() < lightCard.luminance())
            listOf(choice.dark, choice.light).forEach { scheme ->
                val ink = scheme.mailInk()
                assertTrue(contrastRatio(ink, scheme.mailCard()) >= 4.5f)
                assertTrue(contrastRatio(ink, scheme.mailCanvas()) >= 4.5f)
            }
        }
    }

    @Test
    fun inboxGroupsKeepOrderAndDropEmptySections() {
        val zone = ZoneId.of("UTC")
        val weeks = WeekFields.ISO
        val now = java.time.LocalDate.of(2026, 11, 11).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        fun at(year: Int, month: Int, day: Int) =
            java.time.LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val rows = listOf(
            sampleConversation(1, at(2026, 11, 11)).copy(unread = 2, pinned = true),
            sampleConversation(2, at(2026, 11, 10)).copy(latestMms = true),
            sampleConversation(3, at(2026, 11, 9)).copy(unread = 1, latestMms = true),
            sampleConversation(4, at(2026, 11, 2)).copy(pinned = true),
            sampleConversation(5, at(2026, 10, 20)),
        )
        assertEquals("none", inboxGroupMode("nope"))
        assertEquals("date", inboxGroupMode("date"))
        assertTrue(groupInbox(emptyList(), "read", now, zone, weeks).isEmpty())
        val flat = groupInbox(rows, "none", now, zone, weeks)
        assertEquals(listOf(""), flat.map { it.title })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), flat[0].conversations.map { it.threadId })
        val read = groupInbox(rows, "read", now, zone, weeks)
        assertEquals(listOf("Unread", "Read"), read.map { it.title })
        assertEquals(listOf(1L, 3L), read[0].conversations.map { it.threadId })
        assertEquals(listOf(2L, 4L, 5L), read[1].conversations.map { it.threadId })
        val dates = groupInbox(rows, "date", now, zone, weeks)
        assertEquals(listOf("Today", "Yesterday", "This week", "This month", "Older"), dates.map { it.title })
        assertEquals(listOf(1L), dates[0].conversations.map { it.threadId })
        assertEquals(listOf(2L), dates[1].conversations.map { it.threadId })
        assertEquals(listOf(3L), dates[2].conversations.map { it.threadId })
        assertEquals(listOf(4L), dates[3].conversations.map { it.threadId })
        assertEquals(listOf(5L), dates[4].conversations.map { it.threadId })
        assertEquals("Today", receivedBucket(now + 20 * 3_600_000L, now, zone, weeks))
        assertEquals("Older", receivedBucket(0L, now, zone, weeks))
        val types = groupInbox(rows, "type", now, zone, weeks)
        assertEquals(listOf("SMS", "MMS"), types.map { it.title })
        assertEquals(listOf(1L, 4L, 5L), types[0].conversations.map { it.threadId })
        assertEquals(listOf(2L, 3L), types[1].conversations.map { it.threadId })
        val pins = groupInbox(rows, "pinned", now, zone, weeks)
        assertEquals(listOf("Pinned", "Other"), pins.map { it.title })
        assertEquals(listOf(1L, 4L), pins[0].conversations.map { it.threadId })
        assertEquals(listOf(2L, 3L, 5L), pins[1].conversations.map { it.threadId })
        val entries = inboxEntries(rows, "read", now, zone, weeks)
        assertTrue(entries.first() is InboxListEntry.Heading)
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            entries.filterIsInstance<InboxListEntry.Row>().map { it.stripe },
        )
        val trash = trashSections(
            listOf(
                TrashedConversation(rows[4], at(2026, 11, 2)),
                TrashedConversation(rows[0], now),
                TrashedConversation(rows[1], at(2026, 11, 10)),
            ),
            now,
            zone,
            weeks,
        )
        assertEquals(listOf("Today", "Yesterday", "This month"), trash.map { it.title })
        assertEquals(1L, trash[0].items[0].conversation.threadId)
        assertEquals(2L, trash[1].items[0].conversation.threadId)
        assertEquals(5L, trash[2].items[0].conversation.threadId)
    }

    @Test
    fun reorderCountsAHalfRow() {
        assertEquals(0, reorderSlots(49f, 100f, dragging = true))
        assertEquals(1, reorderSlots(50f, 100f, dragging = true))
        assertEquals(2, reorderSlots(160f, 100f, dragging = true))
        assertEquals(0, reorderSlots(-49f, 100f, dragging = true))
        assertEquals(-1, reorderSlots(-50f, 100f, dragging = true))
        assertEquals(-2, reorderSlots(-160f, 100f, dragging = true))
        assertEquals(0, reorderSlots(80f, 100f, dragging = false))
        assertEquals(0, reorderSlots(80f, 0f, dragging = true))
    }

    private fun channelSpread(red: Float, green: Float, blue: Float): Float {
        return maxOf(red, green, blue) - minOf(red, green, blue)
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

    private fun textRetrievePdu(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w(value: Int) = out.write(value)
        fun bytes(value: ByteArray) = out.write(value)
        fun text(value: String) = value.toByteArray() + byteArrayOf(0)
        w(0x8C)
        w(0x84)
        w(0x98)
        bytes(text("tid"))
        val from = byteArrayOf(0x80.toByte()) + text("+15551212")
        w(0x89)
        w(from.size)
        bytes(from)
        val contentType = byteArrayOf(0xB3.toByte())
        w(0x84)
        w(contentType.size)
        bytes(contentType)
        val typeName = text("text/plain")
        val partType = byteArrayOf((typeName.size + 2).toByte()) + typeName + byteArrayOf(0x81.toByte(), 0x84.toByte())
        val name = text("note.txt")
        val header = partType + byteArrayOf(0x8E.toByte()) + name
        val body = byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        w(1)
        w(header.size)
        w(body.size)
        bytes(header)
        bytes(body)
        return out.toByteArray()
    }

    private fun groupRetrievePdu(): ByteArray {
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
        w(0x97)
        bytes(text("+15550001"))
        w(0x97)
        bytes(text("+15550002/TYPE=PLMN"))
        w(0x82)
        bytes(text("pat@example.com"))
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
