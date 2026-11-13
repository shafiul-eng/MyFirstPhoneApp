package com.example.myapp

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class SmartAction(
    val id: Long,
    val title: String,
    val category: String,
    val priority: String,
    val deadline: String,
    val isDone: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash Shield: কোনো অপ্রত্যাশিত এরর হলে অ্যাপ ক্র্যাশ না হয়ে লগ সেভ করবে
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val prefs = getSharedPreferences("crash_shield_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash_log", Log.getStackTraceString(throwable)).apply()
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            val darkColorScheme = darkColorScheme(
                primary = Color(0xFF00F2FE),
                secondary = Color(0xFF7F00FF),
                background = Color(0xFF060814),
                surface = Color(0xFF111625)
            )

            MaterialTheme(colorScheme = darkColorScheme) {
                val context = LocalContext.current
                val crashPrefs = remember { context.getSharedPreferences("crash_shield_prefs", Context.MODE_PRIVATE) }
                var crashLog by remember { mutableStateOf(crashPrefs.getString("last_crash_log", null)) }

                if (crashLog != null) {
                    CrashDiagnosticScreen(
                        errorLog = crashLog!!,
                        onDismiss = {
                            crashPrefs.edit().remove("last_crash_log").apply()
                            crashLog = null
                        }
                    )
                } else {
                    ActionPulseScreen()
                }
            }
        }
    }
}

// ================= CRASH DIAGNOSTIC SCREEN =================
@Composable
fun CrashDiagnosticScreen(errorLog: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060814))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text("⚠️ Crash Shield Report", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
        Spacer(modifier = Modifier.height(8.dp))
        Text("আগের সেশনে একটি ত্রুটি ধরা পড়েছিল:", fontSize = 13.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = errorLog, fontSize = 11.sp, color = Color(0xFFFB7185))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", errorLog))
                    Toast.makeText(context, "এরর লগ কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text("লগ কপি করুন", color = Color.White)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE))
            ) {
                Text("অ্যাপ চালু করুন", color = Color(0xFF060814), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================= OFFLINE AI NLP ENGINE =================
fun analyzeWithOfflineAI(rawInput: String): SmartAction {
    val text = rawInput.trim()
    val lower = text.lowercase(Locale.getDefault())

    val category = when {
        listOf("মিটিং", "অফিস", "ক্লায়েন্ট", "প্রজেক্ট", "রিপোর্ট", "টিম", "meeting", "client", "project").any { lower.contains(it) } -> "অফিস 💼"
        listOf("টাকা", "বিল", "পেমেন্ট", "বেতন", "খরচ", "বাজেট", "হিসাব", "bill", "payment").any { lower.contains(it) } -> "ফাইন্যান্স 💰"
        listOf("কোড", "অ্যাপ", "বাগ", "সার্ভার", "গিটহাব", "সার্কিট", "code", "bug", "app").any { lower.contains(it) } -> "টেক/ডেভ ⚡"
        else -> "পার্সোনাল 🎯"
    }

    val priority = when {
        listOf("জরুরি", "আজই", "এখনই", "দ্রুত", "আর্জেন্ট", "অবশ্যই", "urgent", "asap", "must").any { lower.contains(it) } -> "জরুরি 🔥"
        listOf("পরে", "সময় পেলে", "আগামী সপ্তাহে", "later").any { lower.contains(it) } -> "লো 🧊"
        else -> "মিডিয়াম ⚡"
    }

    val deadline = when {
        lower.contains("আজ") || lower.contains("today") -> "আজকের মধ্যে"
        lower.contains("কাল") || lower.contains("আগামীকাল") || lower.contains("tomorrow") -> "আগামীকাল"
        lower.contains("রাত") || lower.contains("tonight") -> "আজ রাত"
        lower.contains("সকাল") || lower.contains("morning") -> "সকালবেলা"
        else -> "নির্ধারিত নয়"
    }

    return SmartAction(
        id = System.currentTimeMillis(),
        title = text,
        category = category,
        priority = priority,
        deadline = deadline,
        isDone = false
    )
}

// ================= OFFLINE STORAGE =================
fun saveActionsOffline(context: Context, list: List<SmartAction>) {
    try {
        val prefs = context.getSharedPreferences("action_pulse_ai_v3", Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("priority", item.priority)
                put("deadline", item.deadline)
                put("isDone", item.isDone)
            }
            array.put(obj)
        }
        prefs.edit().putString("actions_data", array.toString()).apply()
    } catch (_: Exception) {}
}

fun loadActionsOffline(context: Context): List<SmartAction> {
    val defaultItems = listOf(
        SmartAction(101L, "আগামীকাল সকালে ক্লায়েন্টের সাথে জরুরি প্রজেক্ট মিটিং করতে হবে", "অফিস 💼", "জরুরি 🔥", "আগামীকাল", false),
        SmartAction(102L, "আজ রাতে গিটহাব থেকে নতুন এআই অ্যাপের কোড আপডেট দিতে হবে", "টেক/ডেভ ⚡", "মিডিয়াম ⚡", "আজ রাত", false),
        SmartAction(103L, "অফিসের ইন্টারনেট বিল ও খরচের হিসাব পেমেন্ট করা দরকার", "ফাইন্যান্স 💰", "জরুরি 🔥", "আজকের মধ্যে", true)
    )
    return try {
        val prefs = context.getSharedPreferences("action_pulse_ai_v3", Context.MODE_PRIVATE)
        val data = prefs.getString("actions_data", null) ?: return defaultItems
        val list = mutableListOf<SmartAction>()
        val array = JSONArray(data)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                SmartAction(
                    id = obj.optLong("id", System.currentTimeMillis() + i),
                    title = obj.getString("title"),
                    category = obj.getString("category"),
                    priority = obj.getString("priority"),
                    deadline = obj.optString("deadline", "নির্ধারিত নয়"),
                    isDone = obj.getBoolean("isDone")
                )
            )
        }
        list
    } catch (_: Exception) {
        defaultItems
    }
}

// ================= MAIN FUTURISTIC UI =================
@Composable
fun ActionPulseScreen() {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(loadActionsOffline(context)) }
    var manualInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableStateOf("সব") }

    var isListening by remember { mutableStateOf(false) }
    var liveSpeechText by remember { mutableStateOf("") }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    fun updateActions(newList: List<SmartAction>) {
        actions = newList
        saveActionsOffline(context, newList)
    }

    // ফলব্যাক নেটিভ ভয়েস ডায়ালগ লঞ্চার (যদি ব্যাকগ্রাউন্ড ইঞ্জিন ব্লক থাকে)
    val fallbackVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spoken = matches[0]
                liveSpeechText = "\"$spoken\""
                val extracted = analyzeWithOfflineAI(spoken)
                updateActions(listOf(extracted) + actions)
            }
        }
    }

    fun launchVoiceEngine() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            isListening = false
            return
        }

        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "বাংলা বা ইংরেজিতে আপনার কাজের কমান্ড বলুন...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                fallbackVoiceLauncher.launch(voiceIntent)
                return
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    liveSpeechText = "শুনছি... আপনার কমান্ড বলুন..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    // যদি ইনলাইন ইঞ্জিনে এরর দেয়, স্বয়ংক্রিয়ভাবে সিস্টেম ভয়েস পপআপ ওপেন হবে
                    try {
                        fallbackVoiceLauncher.launch(voiceIntent)
                    } catch (_: Exception) {
                        liveSpeechText = "ভয়েস সার্ভিস পাওয়া যায়নি, নিচে টাইপ করে কমান্ড দিন।"
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spoken = matches[0]
                        liveSpeechText = "\"$spoken\""
                        val extracted = analyzeWithOfflineAI(spoken)
                        updateActions(listOf(extracted) + actions)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) {
                        liveSpeechText = partial[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(voiceIntent)
        } catch (_: Exception) {
            isListening = false
            try {
                fallbackVoiceLauncher.launch(voiceIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "ফোনে ভয়েস রিকগনিশন সাপোর্ট পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVoiceEngine()
        } else {
            Toast.makeText(context, "ভয়েস কমান্ডের জন্য মাইক্রোফোন পারমিশন দিন!", Toast.LENGTH_LONG).show()
        }
    }

    fun onMicButtonClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchVoiceEngine()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF060814), Color(0xFF0F172A), Color(0xFF070913))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ১. ফিউচারিস্টিক হেডার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTION PULSE AI",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00F2FE)
                    )
                    Text(
                        text = "Offline Voice-to-Workflow Engine",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF00F2FE).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "● OFFLINE READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ২. গ্লোয়িং স্ট্যাটাস ড্যাশবোর্ড
            val totalCount = actions.size
            val urgentCount = actions.count { it.priority.contains("জরুরি") && !it.isDone }
            val doneCount = actions.count { it.isDone }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatGlassCard("মোট অ্যাকশন", "$totalCount", Color(0xFF38BDF8), Modifier.weight(1f))
                StatGlassCard("জরুরি কাজ", "$urgentCount", Color(0xFFF43F5E), Modifier.weight(1f))
                StatGlassCard("সম্পন্ন", "$doneCount", Color(0xFF10B981), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ৩. নিওন এআই ভয়েস অরব
            AiVoiceCommandOrb(
                isListening = isListening,
                liveText = liveSpeechText,
                onMicClick = { onMicButtonClick() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ৪. কুইক টেক্সট কমান্ড বার
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = {
                        Text(
                            "কমান্ড লিখুন (যেমন: কাল জরুরি মিটিং)...",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (manualInput.isNotBlank()) {
                            val newAction = analyzeWithOfflineAI(manualInput)
                            updateActions(listOf(newAction) + actions)
                            manualInput = ""
