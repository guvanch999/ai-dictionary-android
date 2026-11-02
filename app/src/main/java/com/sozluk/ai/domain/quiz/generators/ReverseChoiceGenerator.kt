package com.sozluk.ai.domain.quiz.generators

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.translationsFor
import com.sozluk.ai.domain.quiz.Distractors
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.choicePayload
import kotlin.random.Random

/** Native-language translation shown, pick the correct English word from four (spec §7.2). */
class ReverseChoiceGenerator : LocalQuizGenerator {
    override val type = QuizType.REVERSE_CHOICE

    override fun generate(
        target: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
        random: Random,
    ): LocalQuizPayload? {
        val key = { w: WordEntry -> w.term }
        val distractors = Distractors.pick(target, pool, key, random).map(key)
        return choicePayload(
            prompt = target.translationsFor(nativeLang).first,
            correct = target.term,
            distractors = distractors,
            random = random,
        )
    }
}
