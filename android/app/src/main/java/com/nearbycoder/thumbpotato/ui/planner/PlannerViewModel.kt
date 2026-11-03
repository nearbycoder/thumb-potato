package com.nearbycoder.thumbpotato.ui.planner

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.PlaySession
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.data.local.radarSortDate
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.reminders.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class PlannerState(
    val sessions: List<PlaySession> = emptyList(),
    val saved: List<SavedGame> = emptyList(),
    val selectedDay: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = LocalDate.now(),
) {
    /** Followed backlog games that aren't out yet, soonest first (coarse dates at the end of their window). */
    val radar: List<SavedGame>
        get() = saved.filter { it.isFollowing && it.backlogStatus != BacklogStatus.SHELVED && it.isUpcoming() }
            .sortedWith(compareBy<SavedGame> { it.radarSortDate }.thenBy { it.name.lowercase() })
}

class PlannerViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.library
    private val selection = MutableStateFlow(LocalDate.now() to LocalDate.now().minusDays(3))

    val state: StateFlow<PlannerState> = combine(library.playSessions, library.savedGames, selection) { sessions, saved, (day, weekStart) ->
        PlannerState(sessions = sessions, saved = saved, selectedDay = day, weekStart = weekStart)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerState(library.playSessions.value, library.savedGames.value))

    fun selectDay(day: LocalDate) = selection.update { (_, start) ->
        // Keep the strip showing the selected day.
        val newStart = if (day < start || day > start.plusDays(6)) day.minusDays(3) else start
        day to newStart
    }

    fun shiftWeek(days: Long) = selection.update { (day, start) -> day.plusDays(days) to start.plusDays(days) }

    private fun reschedule(context: Context, session: PlaySession) {
        val s = container.settings.current
        Reminders.schedule(context, session, s.remindMinutesBefore, s.sessionReminders)
    }

    fun add(context: Context, session: PlaySession) {
        viewModelScope.launch {
            library.addSession(session)
            reschedule(context, session)
        }
    }

    fun toggleDone(context: Context, session: PlaySession) {
        viewModelScope.launch {
            val updated = session.copy(isDone = !session.isDone)
            library.updateSession(updated)
            reschedule(context, updated)
        }
    }

    fun delete(context: Context, session: PlaySession) {
        viewModelScope.launch {
            Reminders.cancel(context, session)
            library.deleteSession(session)
        }
    }

    fun unfollow(key: String) {
        viewModelScope.launch { library.edit(key) { it.copy(isFollowing = false) } }
    }
}
