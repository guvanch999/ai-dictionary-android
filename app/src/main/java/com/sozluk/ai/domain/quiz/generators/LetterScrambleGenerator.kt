package com.sozluk.ai.domain.quiz.generators

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import kotlin.random.Random

/** Reassemble a word from its shuffled letters (spec §7.2). Needs no distractor pool. */
class LetterScrambleGenerator : LocalQuizGenerator {
    override val type = QuizType.LETTER_SCRAMBLE

    override fun generate(
        target: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
        random: Random,
    ): LocalQuizPayload? {
        val term = target.term
        // Words of 3 or fewer letters are too easy / too likely to scramble back to themselves.
        if (term.length <= 3) return null

        val scrambled = scramble(term, random) ?: return null
        return LocalQuizPayload(
            prompt = scrambled,
            answerText = term,
        )
    }

    /** Returns a letter permutation guaranteed to differ from [word], or null if impossible. */
    private fun scramble(word: String, random: Random): String? {
        // All identical letters (e.g. "aaaa") can never be rearranged differently.
        if (word.toSet().size == 1) return null
        val chars = word.toMutableList()
        repeat(MAX_ATTEMPTS) {
            chars.shuffle(random)
            val candidate = chars.joinToString("")
            if (candidate != word) return candidate
        }
        // Fallback: swap the first two differing characters to guarantee a change.
        val list = word.toMutableList()
        val i = list.indices.first { list[it] != list[0] }
        val tmp = list[0]; list[0] = list[i]; list[i] = tmp
        return list.joinToString("")
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
