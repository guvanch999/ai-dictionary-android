package com.sozluk.ai.domain.quiz.generators

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.Distractors
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.choicePayload
import kotlin.random.Random

/** An example sentence from the word's card with the word blanked out; pick it from four (spec §7.2). */
class ExampleGapGenerator : LocalQuizGenerator {
    override val type = QuizType.EXAMPLE_GAP

    override fun generate(
        target: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
        random: Random,
    ): LocalQuizPayload? {
        val termRegex = Regex(Regex.escape(target.term), RegexOption.IGNORE_CASE)
        val example = target.examples.firstOrNull { it.en.contains(termRegex) } ?: return null

        val prompt = termRegex.replaceFirst(example.en, BLANK)
        val key = { w: WordEntry -> w.term }
        val distractors = Distractors.pick(target, pool, key, random).map(key)
        return choicePayload(
            prompt = prompt,
            correct = target.term,
            distractors = distractors,
            random = random,
        )
    }

    private companion object {
        const val BLANK = "_____"
    }
}
