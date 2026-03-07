package rip.build.courier.domain.model

data class Reaction(
    val rowID: Long,
    val guid: String,
    val targetMessageGUID: String,
    val partIndex: Int = 0,
    val reactionType: ReactionType,
    val emoji: String? = null,
    val isRemoval: Boolean,
    val senderID: String?,
    val isFromMe: Boolean,
    val date: String,
    val sendStatus: SendStatus? = null,
    val sendError: String? = null
) {
    /** The emoji character to display for this reaction. */
    val displayEmoji: String get() = when (reactionType) {
        ReactionType.EMOJI -> emoji ?: "\u2753"
        else -> reactionType.displayEmoji
    }
}

enum class ReactionType(val displayEmoji: String) {
    LOVE("\u2764\uFE0F"),
    LIKE("\uD83D\uDC4D"),
    DISLIKE("\uD83D\uDC4E"),
    LAUGH("\uD83D\uDE02"),
    EMPHASIS("\u203C\uFE0F"),
    QUESTION("\u2753"),
    EMOJI("");

    companion object {
        fun fromString(value: String): ReactionType = when (value) {
            "love" -> LOVE
            "like" -> LIKE
            "dislike" -> DISLIKE
            "laugh" -> LAUGH
            "emphasis" -> EMPHASIS
            "question" -> QUESTION
            "emoji" -> EMOJI
            else -> LIKE
        }
    }
}
