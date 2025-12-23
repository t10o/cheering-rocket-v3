package one.t10o.cheering_rocket.ui.screen.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.t10o.cheering_rocket.data.repository.EventRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * イベント作成画面のUI状態
 */
data class EventCreateUiState(
    val title: String = "",
    val description: String = "",
    val selectedDate: Date? = null,
    val selectedHour: Int = 9,
    val selectedMinute: Int = 0,
    val isCreating: Boolean = false,
    val createdEventId: String? = null,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = title.isNotBlank() && selectedDate != null
    
    val formattedDateTime: String
        get() {
            if (selectedDate == null) return ""
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
            val timeStr = String.format(Locale.JAPAN, "%02d:%02d", selectedHour, selectedMinute)
            return "${dateFormat.format(selectedDate)} $timeStr"
        }
}

/**
 * イベント作成画面のViewModel
 */
@HiltViewModel
class EventCreateViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EventCreateUiState())
    val uiState: StateFlow<EventCreateUiState> = _uiState.asStateFlow()
    
    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }
    
    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }
    
    fun updateDate(date: Date) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }
    
    fun updateTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(
            selectedHour = hour,
            selectedMinute = minute
        )
    }
    
    /**
     * イベントを作成
     */
    fun createEvent() {
        val state = _uiState.value
        if (!state.isValid) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCreating = true,
                errorMessage = null
            )
            
            // 日付と時刻を組み合わせてTimestampを作成
            val calendar = Calendar.getInstance().apply {
                time = state.selectedDate!!
                set(Calendar.HOUR_OF_DAY, state.selectedHour)
                set(Calendar.MINUTE, state.selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDateTime = Timestamp(calendar.time)
            
            eventRepository.createEvent(
                title = state.title,
                description = state.description,
                startDateTime = startDateTime
            )
                .onSuccess { eventId ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdEventId = eventId
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        errorMessage = e.message ?: "イベントの作成に失敗しました"
                    )
                }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

