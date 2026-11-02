package com.sozluk.ai.ui.navigation

import android.net.Uri

// Central definition of navigation routes and their argument keys.
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"

    const val ARG_WORD_ID = "id"
    const val ARG_SESSION_ID = "sessionId"
    const val ARG_SCOPE = "scope"

    // Word ids are String UUIDs (WordEntity.id) — URL-encode to be path-safe.
    const val WORD = "word/{$ARG_WORD_ID}"
    // Session ids are String UUIDs (ExamSession.id) — URL-encode, like WORD.
    const val EXAM = "exam/{$ARG_SESSION_ID}"
    const val EXAM_RESULT = "examResult/{$ARG_SESSION_ID}"
    // Optional preselected scope (e.g. from the Home "N words due" badge).
    const val EXAM_SETUP = "examSetup?$ARG_SCOPE={$ARG_SCOPE}"

    fun word(id: String): String = "word/${Uri.encode(id)}"
    fun exam(sessionId: String): String = "exam/${Uri.encode(sessionId)}"
    fun examResult(sessionId: String): String = "examResult/${Uri.encode(sessionId)}"
    fun examSetup(scope: String = "ALL"): String = "examSetup?$ARG_SCOPE=$scope"
}
