package com.numipad.junior

import android.content.Context
import com.numipad.junior.data.local.NumiPadDatabase
import com.numipad.junior.data.repository.HistoryRepository
import com.numipad.junior.data.repository.PracticeRepository
import com.numipad.junior.data.repository.SettingsRepository
import com.numipad.junior.domain.progress.AppClock
import com.numipad.junior.domain.progress.SystemAppClock
import com.numipad.junior.domain.questions.DefaultRandomProvider
import com.numipad.junior.domain.questions.QuestionGenerator
import com.numipad.junior.domain.questions.RandomProvider
import androidx.room.withTransaction

/** Manual dependency injection. */
class AppContainer(
    context: Context,
    val clock: AppClock = SystemAppClock,
    random: RandomProvider = DefaultRandomProvider,
) {
    private val database: NumiPadDatabase = NumiPadDatabase.create(context)
    val settings = SettingsRepository(context)
    val history = HistoryRepository(database, clock)
    val practice = PracticeRepository(database, QuestionGenerator(random), clock)

    /** Restores defaults: empty history, no sessions, no badges, default settings. */
    suspend fun clearAllData() {
        database.withTransaction {
            history.clear()
            practice.clearProgress()
        }
        settings.clearAll()
    }
}
