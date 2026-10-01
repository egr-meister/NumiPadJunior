package com.numipad.junior.ui.tablet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.numipad.junior.AppContainer
import com.numipad.junior.data.repository.SessionInfo
import com.numipad.junior.data.repository.StartResult
import com.numipad.junior.domain.calculator.CalcInput
import com.numipad.junior.domain.calculator.Calculator
import com.numipad.junior.domain.calculator.CalculatorState
import com.numipad.junior.domain.progress.DailyStats
import com.numipad.junior.domain.progress.ProgressCalculator
import com.numipad.junior.domain.questions.Difficulty
import com.numipad.junior.domain.questions.Topic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PracticeSetup(
    val topic: Topic = Topic.ADDITION,
    val difficulty: Difficulty = Difficulty.EASY,
)

class TabletViewModel(private val container: AppContainer) : ViewModel() {

    private val _calculator = MutableStateFlow(CalculatorState())
    val calculator: StateFlow<CalculatorState> = _calculator.asStateFlow()

    private val _setup = MutableStateFlow(PracticeSetup())
    val setup: StateFlow<PracticeSetup> = _setup.asStateFlow()

    val activeSession: StateFlow<SessionInfo?> = container.practice.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val today: StateFlow<DailyStats> = container.practice.totals
        .map { ProgressCalculator.daily(container.clock.today(), it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressCalculator.daily(container.clock.today(), emptyList()))

    val reduceMotion: StateFlow<Boolean> = container.settings.settings.map { it.reduceMotion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** One-shot navigation events (session id to open). */
    private val _openSession = Channel<Long>(Channel.BUFFERED)
    val openSession = _openSession.receiveAsFlow()

    private val inputLock = Mutex()
    private var restored = false

    init {
        viewModelScope.launch {
            _calculator.value = container.settings.loadCalculator()
            restored = true
            val s = container.settings.settings.first()
            _setup.value = PracticeSetup(s.defaultTopic, s.defaultDifficulty)
        }
    }

    fun onInput(input: CalcInput) {
        viewModelScope.launch {
            inputLock.withLock {
                val outcome = Calculator.reduce(_calculator.value, input)
                _calculator.value = outcome.state
                // One record per evaluation; repeated "=" produces none.
                outcome.record?.let { container.history.add(it) }
                if (restored) container.settings.saveCalculator(outcome.state)
            }
        }
    }

    fun useHistoryValue(value: String) = onInput(CalcInput.UseValue(value))

    fun selectTopic(topic: Topic) = _setup.update { it.copy(topic = topic) }
    fun selectDifficulty(d: Difficulty) = _setup.update { it.copy(difficulty = d) }

    fun startPractice() {
        viewModelScope.launch {
            val s = _setup.value
            when (val r = container.practice.startSession(s.topic, s.difficulty)) {
                is StartResult.Started -> _openSession.send(r.sessionId)
                is StartResult.ActiveExists -> Unit // UI shows resume / end options
            }
        }
    }

    fun endActiveSession() {
        val active = activeSession.value ?: return
        viewModelScope.launch { container.practice.endSession(active.id) }
    }

    /** Re-reads settings and calculator after a data reset elsewhere. */
    fun refreshAfterReset() {
        viewModelScope.launch {
            _calculator.value = container.settings.loadCalculator()
            val s = container.settings.settings.first()
            _setup.value = PracticeSetup(s.defaultTopic, s.defaultDifficulty)
        }
    }
}
