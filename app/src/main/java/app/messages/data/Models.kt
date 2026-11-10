package app.messages.data

enum class InboxListFilter { All, Unread, Read, Archived, Blocked, Contact }

data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val title: String,
    val snippet: String,
    val timestamp: Long,
    val unread: Int,
    val archived: Boolean,
    val pinned: Boolean,
    val draft: String,
    val photoUri: String?,
    val latestMms: Boolean = false,
)

data class PartRef(
    val id: Long,
    val mime: String,
)

data class ChatMessage(
    val smsId: Long?,
    val mmsId: Long?,
    val threadId: Long,
    val address: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
    val status: String,
    val subId: Int,
    val attachments: List<PartRef>,
) {
    val key: String = if (smsId != null) "sms-$smsId" else "mms-$mmsId"
}

data class ContactHit(
    val name: String,
    val number: String,
    val photoUri: String?,
)

data class SimLine(
    val id: Int,
    val slot: Int,
    val carrier: String,
) {
    val shortName: String get() = "SIM ${slot + 1}"
}

data class SearchHit(
    val threadId: Long,
    val title: String,
    val snippet: String,
    val timestamp: Long,
)

object Delivery {
    const val RECEIVED = ""
    const val SENDING = "Sending"
    const val SENT = "Sent"
    const val DELIVERED = "Delivered"
    const val FAILED = "Not sent"
    const val NOT_DELIVERED = "Not delivered"
}
