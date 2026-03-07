package rip.build.courier.ui.chatdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rip.build.courier.domain.model.ReactionType

private val tapbackOptions = listOf(
    ReactionType.LOVE to "love",
    ReactionType.LIKE to "like",
    ReactionType.DISLIKE to "dislike",
    ReactionType.LAUGH to "laugh",
    ReactionType.EMPHASIS to "emphasis",
    ReactionType.QUESTION to "question"
)

@Composable
fun TapbackPicker(
    myReactions: Set<String>,
    onSelect: (type: String, emoji: String?) -> Unit,
    onRemove: (type: String, emoji: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showEmojiInput by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            if (showEmojiInput) {
                EmojiInput(
                    onEmojiSelected = { emoji ->
                        onSelect("emoji", emoji)
                        onDismiss()
                    },
                    onBack = { showEmojiInput = false }
                )
            } else {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tapbackOptions.forEach { (type, apiName) ->
                        val isActive = apiName in myReactions
                        Text(
                            text = type.displayEmoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clickable {
                                    if (isActive) {
                                        onRemove(apiName, null)
                                    } else {
                                        onSelect(apiName, null)
                                    }
                                    onDismiss()
                                }
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    // Emoji picker button
                    Text(
                        text = "+",
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showEmojiInput = true }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiInput(
    onEmojiSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-send when user types an emoji
    LaunchedEffect(text) {
        if (text.isNotEmpty()) {
            // Extract the first emoji/grapheme cluster
            val firstCodePoint = text.codePointAt(0)
            val charCount = Character.charCount(firstCodePoint)
            // Handle multi-codepoint emoji (skin tones, ZWJ sequences, etc.)
            var end = charCount
            while (end < text.length) {
                val nextCodePoint = text.codePointAt(end)
                // Continue if it's a variation selector, ZWJ, skin tone modifier, or combining mark
                if (nextCodePoint == 0xFE0F || nextCodePoint == 0xFE0E ||
                    nextCodePoint == 0x200D ||
                    nextCodePoint in 0x1F3FB..0x1F3FF ||
                    Character.getType(nextCodePoint) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(nextCodePoint) == Character.ENCLOSING_MARK.toInt()
                ) {
                    end += Character.charCount(nextCodePoint)
                } else if (nextCodePoint in 0xE0020..0xE007F) {
                    // Tag characters for flag sequences
                    end += Character.charCount(nextCodePoint)
                } else if (nextCodePoint == 0x20E3) {
                    // Combining enclosing keycap
                    end += Character.charCount(nextCodePoint)
                } else {
                    break
                }
            }
            val emoji = text.substring(0, end)
            onEmojiSelected(emoji)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "\u2190",
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 12.dp)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Type an emoji") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotEmpty()) {
                        onEmojiSelected(text)
                    }
                })
            )
        }
    }
}
