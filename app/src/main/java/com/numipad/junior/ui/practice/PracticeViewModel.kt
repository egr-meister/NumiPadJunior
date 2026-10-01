package com.numipad.junior.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.numipad.junior.AppContainer
import com.numipad.junior.data.repository.SessionDetail
import com.numipad.junior.data.repository.StartResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PracticeEvent {
    data class ShowResults(val sessionId: Long) : PracticeEvent
    data object Ended : PracticeEvent
    data class OpenSession(val sessionId: Long) : PracticeEvent
}

/** Shared by the question, results and review screens of one session. */
class PracticeViewModel(private val container: AppContainer, val sessionId: Long) : ViewModel() {

    val detail: StateFlow<SessionDetail?> = container.practice.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Which question is on screen. Saved index is not needed: after process death the
     * screen opens on the first unanswered question, or on the last answered one whose
     * feedback has not been dismissed yet.
     */
    private val _viewedIndex = MutableStateFlow<Int?>(null)
    val viewedIndex: StateFlow<Int?> = _viewedIndex.asStateFlow()

    private val _events = Channel<PracticeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val lock = Mutex()

    init {
        viewModelScope.launch {
            val first = container.practice.observeSession(sessionId).filterNotNull().first()
            if (_viewedIndex.value == null) _viewedIndex.value = first.currentIndex
        }
    }

    fun answer(questionId: Long, selected: Int) {
        viewModelScope.launch {
            lock.withLock {
                // Repository ignores a second answer for the same question.
                container.practice.answer(questionId, selected)
            }
        }
    }

    fun showIndex(index: Int) {
        _viewedIndex.value = index
    }

    fun next(currentIndex: Int) {
        val d = detail.value ?: return
        if (currentIndex >= d.questions.lastIndex) {
            viewModelScope.launch {
                lock.withLock {
                    if (container.practice.finishSession(sessionId)) _events.send(PracticeEvent.ShowResults(sessionId))
                }
            }
        } else {
            _viewedIndex.value = currentIndex + 1
        }
    }

    fun endSession() {
        viewModelScope.launch {
            lock.withLock {
                container.practice.endSession(sessionId)
                _events.send(PracticeEvent.Ended)
            }
        }
    }

    fun practiceAgain() {
        val d = detail.value ?: return
        viewModelScope.launch {
            when (val r = container.practice.startSession(d.session.topic, d.session.difficulty)) {
                is StartResult.Started -> _events.send(PracticeEvent.OpenSession(r.sessionId))
                is StartResult.ActiveExists -> _events.send(PracticeEvent.OpenSession(r.sessionId))
            }
        }
    }
}
