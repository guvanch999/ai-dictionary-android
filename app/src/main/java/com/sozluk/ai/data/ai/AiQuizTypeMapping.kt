package com.sozluk.ai.data.ai

import com.sozluk.ai.domain.model.QuizType

/**
 * Maps AI quiz types (spec §7.3) to the compact wire strings the model returns in `type` and back.
 * OPEN_SENTENCE (§7.3, open-ended) is graded separately in task 11 and is not requested here.
 */
object AiQuizTypeMapping {

    /** The AI quiz types requested from the model (spec §7.3). */
    val AI_TYPES: List<QuizType> = listOf(
        QuizType.AI_GAP_FILL,
        QuizType.AI_DEFINITION,
        QuizType.AI_SYNONYM,
        QuizType.AI_FIND_MISTAKE,
        QuizType.AI_DIALOG,
    )

    private val toWire: Map<QuizType, String> = mapOf(
        QuizType.AI_GAP_FILL to "gap_fill",
        QuizType.AI_DEFINITION to "definition",
        QuizType.AI_SYNONYM to "synonym",
        QuizType.AI_FIND_MISTAKE to "find_mistake",
        QuizType.AI_DIALOG to "dialog",
    )

    private val fromWire: Map<String, QuizType> = toWire.entries.associate { (k, v) -> v to k }

    fun wireOf(type: QuizType): String? = toWire[type]

    /** Wire string -> [QuizType]; null for an unknown/unsupported type (item is then dropped). */
    fun typeOf(wire: String): QuizType? = fromWire[wire.trim().lowercase()]
}
