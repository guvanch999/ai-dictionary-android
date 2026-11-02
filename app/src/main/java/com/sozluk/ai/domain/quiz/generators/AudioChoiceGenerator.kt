package com.sozluk.ai.domain.quiz.generators

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.Distractors
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.choicePayload
import kotlin.random.Random

/**
 * The term is spoken (TTS), pick the matching written word from four (spec §7.2). [ttsText] holds
 * the term to speak; the actual speech synthesis is the UI's job in task 08.
 */
class AudioChoiceGenerator : LocalQuizGenerator {
    override val type = QuizType.AUDIO_CHOICE

    override fun generate(
        target: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
        random: Random,
    ): LocalQuizPayload? {
        val key = { w: WordEntry -> w.term }
        val distractors = Distractors.pick(target, pool, key, random).map(key)
        return choicePayload(
            prompt = "",
            correct = target.term,
            distractors = distractors,
            random = random,
            ttsText = target.term,
        )
    }
}
