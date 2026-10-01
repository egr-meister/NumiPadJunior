package com.numipad.junior.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.numipad.junior.AppContainer
import com.numipad.junior.ui.history.HistoryScreen
import com.numipad.junior.ui.history.HistoryViewModel
import com.numipad.junior.ui.practice.PracticeScreen
import com.numipad.junior.ui.practice.PracticeViewModel
import com.numipad.junior.ui.practice.ResultsScreen
import com.numipad.junior.ui.practice.ReviewScreen
import com.numipad.junior.ui.progress.ProgressScreen
import com.numipad.junior.ui.progress.ProgressViewModel
import com.numipad.junior.ui.settings.PrivacyScreen
import com.numipad.junior.ui.settings.SettingsScreen
import com.numipad.junior.ui.settings.SettingsViewModel
import com.numipad.junior.ui.tablet.TabletScreen
import com.numipad.junior.ui.tablet.TabletViewModel
import com.numipad.junior.ui.theme.LocalReduceMotion

private object Routes {
    const val TABLET = "tablet"
    const val PRACTICE = "practice/{sessionId}"
    const val RESULTS = "results/{sessionId}"
    const val REVIEW = "review/{sessionId}"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun practice(id: Long) = "practice/$id"
    fun results(id: Long) = "results/$id"
    fun review(id: Long) = "review/$id"
}

@Composable
private inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: () -> VM,
): VM = viewModel(key = key, factory = viewModelFactory { initializer { create() } })

/**
 * Navigation. Back behaviour (predictive-Back compatible, handled by Navigation):
 * practice → main screen (session is kept); review → results; history/progress/settings → parent;
 * main screen → exits the app.
 */
@Composable
fun NumiPadNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    // The tablet ViewModel is scoped to the activity so history can hand it a value.
    val tabletVm = containerViewModel { TabletViewModel(container) }
    val reduceMotion by tabletVm.reduceMotion.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        NavHost(navController = nav, startDestination = Routes.TABLET) {
            composable(Routes.TABLET) {
                TabletScreen(
                    vm = tabletVm,
                    onOpenSession = { id -> nav.navigate(Routes.practice(id)) { launchSingleTop = true } },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) { launchSingleTop = true } },
                    onOpenProgress = { nav.navigate(Routes.PROGRESS) { launchSingleTop = true } },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Routes.PRACTICE, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                val vm = containerViewModel(key = "practice-$id") { PracticeViewModel(container, id) }
                PracticeScreen(
                    vm = vm,
                    onBack = { nav.popBackStack(Routes.TABLET, inclusive = false) },
                    onResults = { sid ->
                        nav.navigate(Routes.results(sid)) { popUpTo(Routes.TABLET) { inclusive = false } }
                    },
                    onEnded = { nav.popBackStack(Routes.TABLET, inclusive = false) },
                )
            }
            composable(Routes.RESULTS, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                val vm = containerViewModel(key = "results-$id") { PracticeViewModel(container, id) }
                ResultsScreen(
                    vm = vm,
                    onReview = { nav.navigate(Routes.review(id)) },
                    onPracticeAgain = { newId ->
                        nav.navigate(Routes.practice(newId)) { popUpTo(Routes.TABLET) { inclusive = false } }
                    },
                    onBackToCalculator = { nav.popBackStack(Routes.TABLET, inclusive = false) },
                )
            }
            composable(Routes.REVIEW, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                val vm = containerViewModel(key = "review-$id") { PracticeViewModel(container, id) }
                ReviewScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) {
                val vm = containerViewModel { HistoryViewModel(container) }
                HistoryScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onUseResult = { value ->
                        tabletVm.useHistoryValue(value)
                        nav.popBackStack(Routes.TABLET, inclusive = false)
                    },
                )
            }
            composable(Routes.PROGRESS) {
                val vm = containerViewModel { ProgressViewModel(container) }
                ProgressScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val vm = containerViewModel { SettingsViewModel(container) }
                SettingsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) { launchSingleTop = true } },
                    onDataReset = { tabletVm.refreshAfterReset() },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
