package com.example.myapp

import android.Manifest
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ডাটা মডেল
data class SmartAction(
    val id: Long,
    val title: String,
    val category: String,
    val priority: String,
    val deadline: String,
    val isDone: Boolean
)

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private val isListeningState = mutableStateOf(false)
    private val liveSpeechText = mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "ভয়েস কমান্ডের জন্য মাইক্রোফোন পারমিশন প্রয়োজন!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        setContent {
            // Cyberpunk Dark Color Palette
            val darkColorScheme = darkColorScheme(
                primary = Color(0xFF00F2FE),
                secondary = Color(0xFF7F00FF),
                background = Color(0xFF070913),
                surface = Color(0xFF111625)
            )

            MaterialTheme(colorScheme = darkColorScheme) {
                ActionPulseScreen(
                    speechRecognizer = speechRecognizer,
                    isListeningState = isListeningState,
                    liveSpeechText = liveSpeechText
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}

// ================= OFFLINE AI NLP ENGINE =================
// ইন্টারনেট ছাড়াই টেক্সট বিশ্লেষণ করে ক্যাটাগরি, প্রায়োরিটি ও ডেডলাইন বের করার ইঞ্জিন
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

// ================= OFFLINE STORAGE (SharedPreferences) =================
fun saveActionsOffline(context: Context, list: List<SmartAction>) {
    val prefs = context.getSharedPreferences("action_pulse_ai_db", Context.MODE_PRIVATE)
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
}

fun loadActionsOffline(context: Context): List<SmartAction> {
    val prefs = context.getSharedPreferences("action_pulse_ai_db", Context.MODE_PRIVATE)
    val data = prefs.getString("actions_data", null) ?: return listOf(
        SmartAction(1, "আগামীকাল সকালে ক্লায়েন্টের সাথে জরুরি প্রজেক্ট মিটিং করতে হবে", "অফিস 💼", "জরুরি 🔥", "আগামীকাল", false),
        SmartAction(2, "আজ রাতে গিটহাব থেকে নতুন এআই অ্যাপের কোড আপডেট দিতে হবে", "টেক/ডেভ ⚡", "মিডিয়াম ⚡", "আজ রাত", false),
        SmartAction(3, "অফিসের ইন্টারনেট বিল ও খরচের হিসাব পেমেন্ট করা দরকার", "ফাইন্যান্স 💰", "জরুরি 🔥", "আজকের মধ্যে", true)
    )
    val list = mutableListOf<SmartAction>()
    val array = JSONArray(data)
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
            SmartAction(
                id = obj.optLong("id", i.toLong()),
                title = obj.getString("title"),
                category = obj.getString("category"),
                priority = obj.getString("priority"),
                deadline = obj.optString("deadline", "নির্ধারিত নয়"),
                isDone = obj.getBoolean("isDone")
            )
        )
    }
    return list
}

// ================= MAIN FUTURISTIC UI =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPulseScreen(
    speechRecognizer: SpeechRecognizer?,
    isListeningState: MutableState<Boolean>,
    liveSpeechText: MutableState<String>
) {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(loadActionsOffline(context)) }
    var manualInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Actions, 1 = Auto Report

    fun updateActions(newList: List<SmartAction>) {
        actions = newList
        saveActionsOffline(context, newList)
    }

    // ভয়েস লিসেনার ফাংশন
    fun triggerVoiceCapture() {
        if (isListeningState.value) {
            speechRecognizer?.stopListening()
            isListeningState.value = false
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListeningState.value = true
                liveSpeechText.value = "শুনছি... আপনার কমান্ড বলুন..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningState.value = false }
            override fun onError(error: Int) {
                isListeningState.value = false
                liveSpeechText.value = "ভয়েস বোঝা যায়নি, আবার চেষ্টা করুন বা নিচে টাইপ করুন।"
            }
            override fun onResults(results: Bundle?) {
                isListeningState.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    liveSpeechText.value = "\"$spoken\""
                    val extracted = analyzeWithOfflineAI(spoken)
                    updateActions(listOf(extracted) + actions)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    liveSpeechText.value = partial[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    // Background Gradient
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF060814), Color(0xFF0F172A), Color(0xFF070913))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        text = "● 100% OFFLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // ৩. নিওন এআই ভয়েস অরব (AI Voice Command Hub)
            AiVoiceCommandOrb(
                isListening = isListeningState.value,
                liveText = liveSpeechText.value,
                onMicClick = { triggerVoiceCapture() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ৪. কুইক টেক্সট কমান্ড বার (মাইক্রোফোন ছাড়াও টেস্ট করার জন্য)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("অথবা এখানে কমান্ড লিখুন (যেমন: কাল জরুরি মিটিং)...", fontSize = 13.sp, color = Color(0xFF64748B)) },
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
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))
                        )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Add", tint = Color(0xFF060814))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ৫. ট্যাব সুইচার (Action Tasks vs Smart Report Draft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111625))
                    .padding(4.dp)
            ) {
                TabPillButton(
                    title = "⚡ অ্যাকশন লিস্ট (${actions.size})",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabPillButton(
                    title = "📄 অটো-রিপোর্ট ড্রাফট",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ৬. মেইন কনটেন্ট এরিয়া
            if (selectedTab == 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(actions) { index, item ->
                        ActionNeonCard(
                            item = item,
                            onToggleDone = {
                                val mutable = actions.toMutableList()
                                mutable[index] = item.copy(isDone = !item.isDone)
                                updateActions(mutable)
                            },
                            onCalendarClick = {
                                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, item.title)
                                    putExtra(CalendarContract.Events.DESCRIPTION, "Category: ${item.category} | Priority: ${item.priority}")
                                }
                                try {
                                    context.startActivity(calIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ক্যালেন্ডার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                val mutable = actions.toMutableList()
                                mutable.removeAt(index)
                                updateActions(mutable)
                            }
                        )
                    }
                }
            } else {
                // অটোমেটিক রিপোর্ট জেনারেটর স্ক্রিন
                AutoReportGeneratorView(actions = actions, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ================= UI COMPONENTS =================

@Composable
fun StatGlassCard(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111827).copy(alpha = 0.85f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
fun AiVoiceCommandOrb(isListening: Boolean, liveText: String, onMicClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.18f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 550 else 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isListening) Color(0xFFF43F5E) else Color(0xFF00F2FE),
        label = "color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF111827), Color(0xFF1E1B4B), Color(0xFF111827))
                )
            )
            .border(1.5.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
   
