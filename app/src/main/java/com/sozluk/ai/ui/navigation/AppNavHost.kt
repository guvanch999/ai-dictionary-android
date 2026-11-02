package com.sozluk.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sozluk.ai.domain.srs.ExamScope
import com.sozluk.ai.ui.screens.exam.ExamResultScreen
import com.sozluk.ai.ui.screens.exam.ExamScreen
import com.sozluk.ai.ui.screens.exam.ExamSetupScreen
import com.sozluk.ai.ui.screens.home.HomeScreen
import com.sozluk.ai.ui.screens.settings.SettingsScreen
import com.sozluk.ai.ui.screens.word.WordScreen

@Composable
fun AppNavHost(deepLinkWordId: String? = null) {
    val navController = rememberNavController()

    // Deep link from the lookup card's "Open in app": jump to the word over Home.
    LaunchedEffect(deepLinkWordId) {
        if (deepLinkWordId != null) navController.navigate(Routes.word(deepLinkWordId))
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenWord = { id -> navController.navigate(Routes.word(id)) },
                onOpenExamSetup = { scope -> navController.navigate(Routes.examSetup(scope)) },
                onContinueExam = { sessionId -> navController.navigate(Routes.exam(sessionId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.WORD,
            arguments = listOf(navArgument(Routes.ARG_WORD_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString(Routes.ARG_WORD_ID).orEmpty()
            WordScreen(wordId = wordId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.EXAM_SETUP,
            arguments = listOf(
                navArgument(Routes.ARG_SCOPE) {
                    type = NavType.StringType
                    defaultValue = "ALL"
                },
            ),
        ) { backStackEntry ->
            val scope = runCatching {
                ExamScope.valueOf(backStackEntry.arguments?.getString(Routes.ARG_SCOPE) ?: "ALL")
            }.getOrDefault(ExamScope.ALL)
            ExamSetupScreen(
                onStartExam = { sessionId ->
                    navController.navigate(Routes.exam(sessionId)) {
                        // Don't return to setup after the exam has started.
                        popUpTo(Routes.EXAM_SETUP) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                initialScope = scope,
            )
        }
        composable(
            route = Routes.EXAM,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.ARG_SESSION_ID).orEmpty()
            ExamScreen(
                sessionId = sessionId,
                onFinish = { finishedId ->
                    navController.navigate(Routes.examResult(finishedId)) {
                        popUpTo(Routes.EXAM) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            route = Routes.EXAM_RESULT,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.ARG_SESSION_ID).orEmpty()
            ExamResultScreen(
                sessionId = sessionId,
                onOpenWord = { id -> navController.navigate(Routes.word(id)) },
                onAgain = {
                    navController.navigate(Routes.examSetup()) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
