package com.numipad.junior.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.numipad.junior.domain.calculator.CalcError
import com.numipad.junior.domain.calculator.CalcOperator
import com.numipad.junior.domain.calculator.CalculatorState
import com.numipad.junior.domain.calculator.CompletedCalculation
import com.numipad.junior.domain.questions.Difficulty
import com.numipad.junior.domain.questions.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "numipad_prefs")

data class AppSettings(
    val defaultTopic: Topic = Topic.ADDITION,
    val defaultDifficulty: Difficulty = Difficulty.EASY,
    val reduceMotion: Boolean = false,
)

/** Preferences and the in-progress calculator input, stored with DataStore. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val topic = stringPreferencesKey("default_topic")
        val difficulty = stringPreferencesKey("default_difficulty")
        val reduceMotion = booleanPreferencesKey("reduce_motion")

        val calcFirst = stringPreferencesKey("calc_first")
        val calcOperator = stringPreferencesKey("calc_operator")
        val calcSecond = stringPreferencesKey("calc_second")
        val doneFirst = stringPreferencesKey("calc_done_first")
        val doneOperator = stringPreferencesKey("calc_done_operator")
        val doneSecond = stringPreferencesKey("calc_done_second")
        val doneResult = stringPreferencesKey("calc_done_result")
        val doneRounded = booleanPreferencesKey("calc_done_rounded")
        val error = stringPreferencesKey("calc_error")

        val calcKeys = listOf(calcFirst, calcOperator, calcSecond, doneFirst, doneOperator, doneSecond, doneResult, error)
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            defaultTopic = Topic.fromName(p[Keys.topic]) ?: Topic.ADDITION,
            defaultDifficulty = Difficulty.fromName(p[Keys.difficulty]) ?: Difficulty.EASY,
            reduceMotion = p[Keys.reduceMotion] ?: false,
        )
    }

    suspend fun setDefaultTopic(topic: Topic) = store.edit { it[Keys.topic] = topic.name }
    suspend fun setDefaultDifficulty(d: Difficulty) = store.edit { it[Keys.difficulty] = d.name }
    suspend fun setReduceMotion(value: Boolean) = store.edit { it[Keys.reduceMotion] = value }

    suspend fun loadCalculator(): CalculatorState {
        val p = store.data.first()
        val done = run {
            val op = CalcOperator.fromName(p[Keys.doneOperator])
            val result = p[Keys.doneResult]
            if (op != null && result != null) {
                CompletedCalculation(
                    first = p[Keys.doneFirst].orEmpty(),
                    operator = op,
                    second = p[Keys.doneSecond].orEmpty(),
                    result = result,
                    rounded = p[Keys.doneRounded] ?: false,
                )
            } else {
                null
            }
        }
        return CalculatorState(
            first = p[Keys.calcFirst].orEmpty(),
            operator = CalcOperator.fromName(p[Keys.calcOperator]),
            second = p[Keys.calcSecond].orEmpty(),
            completed = done,
            error = p[Keys.error]?.let { name -> CalcError.entries.firstOrNull { it.name == name } },
        )
    }

    suspend fun saveCalculator(s: CalculatorState) {
        store.edit { p ->
            Keys.calcKeys.forEach { p.remove(it) }
            p.remove(Keys.doneRounded)
            p[Keys.calcFirst] = s.first
            s.operator?.let { p[Keys.calcOperator] = it.name }
            p[Keys.calcSecond] = s.second
            s.error?.let { p[Keys.error] = it.name }
            s.completed?.let {
                p[Keys.doneFirst] = it.first
                p[Keys.doneOperator] = it.operator.name
                p[Keys.doneSecond] = it.second
                p[Keys.doneResult] = it.result
                p[Keys.doneRounded] = it.rounded
            }
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
