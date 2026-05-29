package com.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.Message
import com.example.data.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository

    init {
        val messageDao = AppDatabase.getDatabase(application).messageDao()
        repository = MessageRepository(messageDao)
    }

    // Observing history from DB
    val messages: StateFlow<List<Message>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _apiKey = MutableStateFlow(BuildConfig.GEMINI_API_KEY)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialSpeech = MutableStateFlow("")
    val partialSpeech: StateFlow<String> = _partialSpeech.asStateFlow()

    private val _isAmbientMode = MutableStateFlow(true) // Defaults to ambient conversation
    val isAmbientMode: StateFlow<Boolean> = _isAmbientMode.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    // Trigger TTS playout in Activity
    private val _speakTrigger = MutableSharedFlow<String>()
    val speakTrigger: SharedFlow<String> = _speakTrigger.asSharedFlow()

    // Trigger starting Speech Recognition in Activity
    private val _startSpeechTrigger = MutableSharedFlow<Unit>()
    val startSpeechTrigger: SharedFlow<Unit> = _startSpeechTrigger.asSharedFlow()

    // Trigger stopping Speech Recognition in Activity
    private val _stopSpeechTrigger = MutableSharedFlow<Unit>()
    val stopSpeechTrigger: SharedFlow<Unit> = _stopSpeechTrigger.asSharedFlow()

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (!listening) {
            _partialSpeech.value = ""
        }
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun setAmbientMode(enabled: Boolean) {
        _isAmbientMode.value = enabled
        // If turned off, we should stop listening
        if (!enabled && _isListening.value) {
            viewModelScope.launch {
                _stopSpeechTrigger.emit(Unit)
            }
        }
    }

    fun setSettingsOpen(open: Boolean) {
        _isSettingsOpen.value = open
    }

    fun clearError() {
        _errorText.value = null
    }

    fun updatePartialSpeech(text: String) {
        _partialSpeech.value = text
    }

    fun handleSpeechError(errorDescription: String) {
        _partialSpeech.value = ""
        _isListening.value = false
        // Avoid disturbing the user unless it's a persistent issue, but log it
        Log.e("MQLiveSpeech", "Speech error occurred: $errorDescription")
    }

    // Main entry point for user voice search or typed message
    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save user message to database
            val userMessage = Message(sender = "user", text = text)
            repository.insert(userMessage)

            _isGenerating.value = true
            _errorText.value = null

            val currentKey = _apiKey.value.trim()
            if (currentKey.isEmpty() || currentKey == "MY_GEMINI_API_KEY") {
                _errorText.value = "لطفاً ابتدا کلید API معتبر جمینای را در تنظیمات وارد کنید"
                _isGenerating.value = false
                return@launch
            }

            try {
                // Construct history context for Gemini
                val recentMessages = messages.value.takeLast(10)
                val contentsList = recentMessages.map { msg ->
                    Content(
                        parts = listOf(Part(text = msg.text)),
                        role = if (msg.sender == "user") "user" else "model"
                    )
                }

                val systemPrompt = """
                    نام شما دستیار لوکس هوشمند "M.Q.live" است.
                    قوانین بسیار مهم:
                    1. تمام پاسخ‌ها را به زبان فارسی روان، جذاب، ادبی و صوتی بنویسید.
                    2. جواب‌ها باید نسبتاً خلاصه، شیرین و شیوا باشند طوری که برای خواندن با صدای بلند بسیار مناسب باشد.
                    3. هرگز از کاراکتر‌های فرمت‌دهی سنگین نظیر ستاره (*)، لیست‌های بالت دار خیلی طولانی، خط تیره یا کدهای برنامه‌نویسی زیاد استفاده نکنید تا موتور تبدیل متن به صدا (TTS) بدون عیب و نقص متن را برای کاربر با صدای بلند بخواند.
                    4. لحنی شیک، پاسخگو و فوق‌العاده باکلاس داشته باشید.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = contentsList,
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey = currentKey, request = request)
                }

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrBlank()) {
                    // Save Gemini reply to DB
                    val aiMessage = Message(sender = "ai", text = replyText)
                    repository.insert(aiMessage)

                    // Trigger Speak out loud
                    _speakTrigger.emit(replyText)
                } else {
                    _errorText.value = "خطا در دریافت پاسخ از هوش مصنوعی"
                }

            } catch (e: Exception) {
                Log.e("MQLive", "Gemini API failed", e)
                _errorText.value = "خطای ارتباط: ${e.localizedMessage}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun startListening() {
        viewModelScope.launch {
            _startSpeechTrigger.emit(Unit)
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            _stopSpeechTrigger.emit(Unit)
        }
    }
}
