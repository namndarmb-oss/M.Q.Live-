package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.theme.LuxuryBg
import com.example.ui.theme.LuxuryBorder
import com.example.ui.theme.LuxuryGlowIndigo
import com.example.ui.theme.LuxuryOnBg
import com.example.ui.theme.LuxuryOnSurface
import com.example.ui.theme.LuxuryPrimary
import com.example.ui.theme.LuxurySecondary
import com.example.ui.theme.LuxurySurface
import com.example.ui.theme.LuxurySurfaceVariant
import com.example.ui.theme.LuxuryTertiary
import com.example.ui.theme.LuxuryTextMuted
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val restartListeningRunnable = Runnable {
        if (viewModel.isAmbientMode.value && !viewModel.isSpeaking.value && !viewModel.isGenerating.value) {
            startSpeechRecognition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize text to speech engine
        initTextToSpeech()

        // 2. Initialize speech-to-text listener references
        initSpeechRecognizer()

        // 3. Connect ViewModel signals
        lifecycleScopeLaunch()

        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = LuxuryBg
                    ) { innerPadding ->
                        MQLiveDashboardScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            onRequestMicPermission = { startSpeechRecognition() }
                        )
                    }
                }
            }
        }
    }

    private fun lifecycleScopeLaunch() {
        // Collect speech requests
        lifecycleScope.launch {
            viewModel.speakTrigger.collect { text ->
                speakOutLoud(text)
            }
        }

        // Collect manual STT triggers
        lifecycleScope.launch {
            viewModel.startSpeechTrigger.collect {
                startSpeechRecognition()
            }
        }

        lifecycleScope.launch {
            viewModel.stopSpeechTrigger.collect {
                stopSpeechRecognition()
            }
        }
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Configure Persian if supported, or fallback nicely
                val persianLocale = Locale("fa", "IR")
                val langResult = tts?.setLanguage(persianLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("MQLiveTTS", "Persian language package is missing/unsupported. Fallback to default engine.")
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        viewModel.setSpeaking(true)
                        // Make sure we stop listening to avoid hearing ourselves
                        stopSpeechRecognition()
                    }

                    override fun onDone(utteranceId: String?) {
                        viewModel.setSpeaking(false)
                        triggerDelayedListening()
                    }

                    override fun onError(utteranceId: String?) {
                        viewModel.setSpeaking(false)
                        triggerDelayedListening()
                    }
                })
            } else {
                Log.e("MQLiveTTS", "TextToSpeech initialization failed.")
            }
        }
    }

    private fun triggerDelayedListening() {
        mainHandler.removeCallbacks(restartListeningRunnable)
        if (viewModel.isAmbientMode.value) {
            mainHandler.postDelayed(restartListeningRunnable, 1000)
        }
    }

    private fun speakOutLoud(text: String) {
        runOnUiThread {
            try {
                tts?.stop()
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MQLIVE_SPEAK")
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MQLIVE_SPEAK")
                viewModel.setSpeaking(true)
            } catch (e: Exception) {
                Log.e("MQLiveTTS", "Speak failed", e)
                viewModel.setSpeaking(false)
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("MQLiveSpeech", "Speech recognition not available on this device.")
            return
        }
        if (speechRecognizer != null) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    viewModel.setListening(true)
                    viewModel.updatePartialSpeech("من آماده‌ام، بفرمایید...")
                }

                override fun onBeginningOfSpeech() {
                    viewModel.updatePartialSpeech("درحال شنیدن صدای شما...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Level can be mapped if needed
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    viewModel.updatePartialSpeech("درحال تبدیل صدا به متن...")
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "خطای ضبط صدا"
                        SpeechRecognizer.ERROR_CLIENT -> "خطای کلاینت"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "عدم اجازه دسترسی به میکروفون"
                        SpeechRecognizer.ERROR_NETWORK -> "خطای اتصال شبکه"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "خطای زمان اتصال شبکه"
                        SpeechRecognizer.ERROR_NO_MATCH -> "پیامی شنیده نشد"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس میکروفون مشغول است"
                        SpeechRecognizer.ERROR_SERVER -> "خطای سرور گوگل"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "سکوت طولانی"
                        else -> "خطای صوتی دیگر"
                    }
                    viewModel.handleSpeechError(message)

                    // Ambient Mode continuous loop fallback
                    if (viewModel.isAmbientMode.value) {
                        triggerDelayedListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    viewModel.setListening(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val outText = matches?.firstOrNull()
                    if (!outText.isNullOrBlank()) {
                        viewModel.updatePartialSpeech(outText)
                        viewModel.sendUserMessage(outText)
                    } else {
                        if (viewModel.isAmbientMode.value) {
                            triggerDelayedListening()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val pText = matches?.firstOrNull()
                    if (!pText.isNullOrBlank()) {
                        viewModel.updatePartialSpeech(pText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startSpeechRecognition() {
        runOnUiThread {
            try {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "لطفاً دسترسی میکروفون را فعال کنید.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                // Stop active speaking
                tts?.stop()
                viewModel.setSpeaking(false)

                initSpeechRecognizer()
                speechRecognizer?.cancel()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
                viewModel.setListening(true)
            } catch (e: Exception) {
                Log.e("MQLiveSTT", "Error starting speech integration", e)
                viewModel.setListening(false)
            }
        }
    }

    private fun stopSpeechRecognition() {
        runOnUiThread {
            try {
                speechRecognizer?.stopListening()
                viewModel.setListening(false)
                mainHandler.removeCallbacks(restartListeningRunnable)
            } catch (e: Exception) {
                Log.e("MQLiveSTT", "Stop listening err", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        mainHandler.removeCallbacks(restartListeningRunnable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MQLiveDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val partialSpeech by viewModel.partialSpeech.collectAsState()
    val isAmbientMode by viewModel.isAmbientMode.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val errorText by viewModel.errorText.collectAsState()

    var typedText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    // Request Mic permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onRequestMicPermission()
        } else {
            Toast.makeText(context, "M.Q.live برای کارکرد نیاز مبرم به دسترسی صدا دارد.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-scroll to bottom on message updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    // Always keep Listening loop active on start if Ambient is ON
    LaunchedEffect(isAmbientMode) {
        if (isAmbientMode && !isListening && !isSpeaking && !isGenerating) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onRequestMicPermission()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(LuxuryBg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header - Luxury Design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings button
                IconButton(
                    onClick = { viewModel.setSettingsOpen(true) },
                    modifier = Modifier
                        .size(46.dp)
                        .background(LuxurySurface.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, LuxuryBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "تنظیمات",
                        tint = LuxuryOnBg
                    )
                }

                // Brand Center Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "M.Q.live",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            brush = Brush.horizontalGradient(
                                listOf(LuxuryPrimary, LuxurySecondary, LuxuryTertiary)
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    // Ambient Status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    when {
                                        isListening -> LuxuryPrimary
                                        isGenerating -> LuxurySecondary
                                        isSpeaking -> LuxuryTertiary
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isListening -> "درحال شنیدن..."
                                isGenerating -> "درحال اندیشیدن..."
                                isSpeaking -> "درحال سخن گفتن..."
                                else -> "آماده به کار"
                            },
                            color = LuxuryTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Reset chat history / Sweep button
                IconButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        Toast.makeText(context, "تاریخچه گفتگو پاکسازی شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(LuxurySurface.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, LuxuryBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("history_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "پاک کردن پیام‌ها",
                        tint = LuxuryTertiary.copy(alpha = 0.9f)
                    )
                }
            }

            // Error Text presentation if present
            AnimatedVisibility(
                visible = errorText != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                errorText?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFF87171).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن خطا",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.clearError() }
                            )
                        }
                    }
                }
            }

            // Central Message List / Greeting empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // Luxurious empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .shadow(24.dp, CircleShape, spotColor = LuxuryPrimary)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            LuxuryPrimary.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .border(1.dp, LuxuryPrimary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "لوگو",
                                tint = LuxurySecondary,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "به M.Q.live خوش آمدید",
                            color = LuxuryOnBg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "دستیار صوتی همواره بیدار، لوکس و پاسخگوی شما.\nکافیست لب به سخن بگشایید تا با صدای بلند پاسخ دهد.",
                            color = LuxuryTextMuted,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        // Fast interactive triggers
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QuickSuggestCard(
                                title = "شعر زیبایی بخوان",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.sendUserMessage("شعر زیبایی را فرسی برای من بخوان") }
                            )
                            QuickSuggestCard(
                                title = "قوانین موفقیت چیست؟",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.sendUserMessage("۳ قانون کلیدی موفقیت در زندگی را بگو") }
                            )
                        }
                    }
                } else {
                    // Chat messages List with luxury styling
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(messages) { msg ->
                            val isAi = msg.sender == "ai"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAi) LuxurySurface.copy(alpha = 0.85f) else LuxuryPrimary.copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isAi) 4.dp else 16.dp,
                                        bottomEnd = if (isAi) 16.dp else 4.dp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .border(
                                            width = 1.dp,
                                            color = if (isAi) LuxuryBorder.copy(alpha = 0.4f) else LuxuryPrimary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isAi) 4.dp else 16.dp,
                                                bottomEnd = if (isAi) 16.dp else 4.dp
                                            )
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = if (isAi) "M.Q.live" else "شما",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isAi) LuxurySecondary else LuxuryTertiary,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        Text(
                                            text = msg.text,
                                            color = LuxuryOnSurface,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Preview Speech Real-time layout (Speech Recognition status / live stream)
            AnimatedVisibility(
                visible = isListening && partialSpeech.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .background(LuxurySurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .border(1.dp, LuxuryPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partialSpeech,
                        color = LuxurySecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keyboard/text input layout (hidden by default, can be toggled to allow typing)
            var showKeyboardInput by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = showKeyboardInput,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(LuxurySurface, RoundedCornerShape(24.dp))
                        .border(1.dp, LuxuryBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { typedText = it },
                        placeholder = { Text("چیزی بنویسید...", color = LuxuryTextMuted) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = LuxuryOnBg,
                            unfocusedTextColor = LuxuryOnBg
                        ),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = TextStyle(fontSize = 14.sp)
                    )

                    // Send text message button
                    IconButton(
                        onClick = {
                            if (typedText.isNotBlank()) {
                                viewModel.sendUserMessage(typedText.trim())
                                typedText = ""
                                showKeyboardInput = false // auto collapse
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(LuxuryPrimary, CircleShape)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "فرستادن",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // bottom floating ambient pill bar & controller
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {

                // Siri/Gemini Live style animated math-driven dynamic glowing wave
                LuxuryVoiceOrb(
                    isListening = isListening,
                    isGenerating = isGenerating,
                    isSpeaking = isSpeaking,
                    modifier = Modifier.fillMaxWidth()
                )

                // Layout controls resting on top of the wave
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Toggle Keyboard/Manual mode button
                    IconButton(
                        onClick = { showKeyboardInput = !showKeyboardInput },
                        modifier = Modifier
                            .size(46.dp)
                            .background(LuxurySurface.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, LuxuryBorder.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (showKeyboardInput) Icons.Default.Mic else Icons.Default.Keyboard,
                            contentDescription = "تغییر ورودی",
                            tint = LuxurySecondary
                        )
                    }

                    // MAIN Central Glowing Voice Button (With pulsating effect)
                    val scaleFactor by animateFloatAsState(
                        targetValue = if (isListening) 1.25f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pulse"
                    )

                    Box(
                        modifier = Modifier
                            .offset(y = (-14).dp)
                            .size((62.dp.value * scaleFactor).dp)
                            .shadow(16.dp, CircleShape, spotColor = LuxuryPrimary)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(LuxuryPrimary, LuxuryGlowIndigo)
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, LuxurySecondary.copy(alpha = 0.8f), CircleShape)
                            .clickable {
                                if (isListening) {
                                    viewModel.stopListening()
                                } else {
                                    val permissionCheck = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    )
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        onRequestMicPermission()
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            .testTag("voice_pulsar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "میکروفون صوتی",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Ambient Mode continuous conversation switch / indicator
                    IconButton(
                        onClick = {
                            val nextMode = !isAmbientMode
                            viewModel.setAmbientMode(nextMode)
                            Toast.makeText(
                                context,
                                if (nextMode) "گفتگوی پیوسته فعال شد" else "گفتگوی پیوسته غیرفعال شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (isAmbientMode) LuxuryTertiary.copy(alpha = 0.15f) else LuxurySurface.copy(alpha = 0.8f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isAmbientMode) LuxuryTertiary.copy(alpha = 0.5f) else LuxuryBorder.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .testTag("ambient_mode_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "گفتگوی مداوم",
                            tint = if (isAmbientMode) LuxuryTertiary else LuxuryOnSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Luxury configuration settings Dialog
        if (isSettingsOpen) {
            SettingsDialog(
                viewModel = viewModel,
                onClose = { viewModel.setSettingsOpen(false) }
            )
        }
    }
}

@Composable
fun QuickSuggestCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(1.dp, LuxuryBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = LuxurySurface.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardVoice,
                contentDescription = "پیشنهاد",
                tint = LuxuryPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                title,
                color = LuxuryOnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "بپرسید",
                    color = LuxurySecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = LuxurySecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val currentApiKey by viewModel.apiKey.collectAsState()
    var tempApiKey by remember { mutableStateOf(currentApiKey) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = LuxurySurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, LuxuryPrimary.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تنظیمات M.Q.live",
                        color = LuxuryOnBg,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = LuxuryTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "کلید API شخصی جمینای را وارد کنید:",
                    color = LuxuryOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = tempApiKey,
                    onValueChange = { tempApiKey = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = LuxuryOnBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input_field"),
                    placeholder = { Text("GEMINI_API_KEY", color = LuxuryTextMuted.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryPrimary,
                        unfocusedBorderColor = LuxuryBorder,
                        focusedContainerColor = LuxuryBg,
                        unfocusedContainerColor = LuxuryBg
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "نکته: کلید شما به صورت محلی بر روی دستگاه شما نگه‌داری می‌شود تا امنیت ۱۰۰٪ تضمین گردد.",
                    color = LuxuryTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            viewModel.clearChatHistory()
                            Toast.makeText(viewModel.getApplication(), "کل تاریخچه با موفقیت پاک شد", Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("پاکسازی کامل", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.updateApiKey(tempApiKey)
                            Toast.makeText(viewModel.getApplication(), "تنظیمات با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ذخیره", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LuxuryVoiceOrb(
    isListening: Boolean,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phaseShift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phaseShift2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val phaseShift3 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 3f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    val targetAmplitude = when {
        isListening -> 48f
        isGenerating -> 18f
        isSpeaking -> 56f
        else -> 6f
    }

    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "amplitude"
    )

    val color1 = LuxuryPrimary
    val color2 = LuxurySecondary
    val color3 = LuxuryTertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                clip = false,
                ambientColor = LuxuryPrimary.copy(alpha = 0.5f),
                spotColor = LuxurySecondary.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LuxurySurface.copy(alpha = 0.95f),
                        LuxuryBg
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        LuxuryPrimary.copy(alpha = 0.5f),
                        LuxurySecondary.copy(alpha = 0.2f),
                        LuxuryTertiary.copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Wave 1
            val path1 = Path()
            path1.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                val envelope = Math.sin((normalizedX * Math.PI)).toFloat()
                val y = centerY + (amplitude * envelope * Math.sin((x * 0.012f + phaseShift1).toDouble()).toFloat())
                path1.lineTo(x.toFloat(), y)
            }
            drawPath(
                path = path1,
                color = color1.copy(alpha = 0.35f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Wave 2
            val path2 = Path()
            path2.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                val envelope = Math.sin((normalizedX * Math.PI)).toFloat()
                val y = centerY + (amplitude * 0.75f * envelope * Math.sin((x * 0.018f + phaseShift2 + 1.2f).toDouble()).toFloat())
                path2.lineTo(x.toFloat(), y)
            }
            drawPath(
                path = path2,
                color = color2.copy(alpha = 0.45f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Wave 3
            val path3 = Path()
            path3.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                val envelope = Math.sin((normalizedX * Math.PI)).toFloat()
                val y = centerY + (amplitude * 0.45f * envelope * Math.sin((x * 0.024f + phaseShift3 + 2.2f).toDouble()).toFloat())
                path3.lineTo(x.toFloat(), y)
            }
            drawPath(
                path = path3,
                color = color3.copy(alpha = 0.35f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
