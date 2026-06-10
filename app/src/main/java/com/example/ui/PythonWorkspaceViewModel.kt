package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.PythonDatabase
import com.example.data.PythonFile
import com.example.data.PythonFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PythonWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PythonDatabase.getDatabase(application)
    private val repository = PythonFileRepository(database.pythonFileDao())

    // All workspace files stored in DB
    val allFiles: StateFlow<List<PythonFile>> = repository.allFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current open workspace file states
    var currentFileName by mutableStateOf("main.py")
    var currentFileContent by mutableStateOf("")
    var consoleLogs by mutableStateOf<List<ConsoleMessage>>(emptyList())
    var engineStatus by mutableStateOf("מכין מנוע...")
    var isRunning by mutableStateOf(false)
    var isEngineLoaded by mutableStateOf(false)

    // UI state controllers
    var isNewFileDialogOpen by mutableStateOf(false)
    var isPackageDialogOpen by mutableStateOf(false)
    var inputRequestPrompt by mutableStateOf<String?>(null)
    private var inputContinuation: ((String) -> Unit)? = null

    // Library Manager states
    var installedPackages by mutableStateOf<List<String>>(
        listOf("numpy", "pandas", "matplotlib", "sympy") // Default/Preloaded packages
    )
    var packageSearchQuery by mutableStateOf("")
    var isInstallingPackage by mutableStateOf(false)
    var installProgressText by mutableStateOf("")

    // Gemini AI helper states
    var aiChatHistory by mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "assistant",
                message = "שלום! אני עוזר הפייתון האישי שלך. אני יכול לעזור לך לכתוב קוד, למצוא באגים ושגיאות הרצה, להסביר תפקודי ספריות ולייצר עבורך משחקים וממשקים מהממים בקנווס. מה תרצה לעשות?"
            )
        )
    )
    var aiInputMessage by mutableStateOf("")
    var isAiGenerating by mutableStateOf(false)

    // Console output list structures
    data class ConsoleMessage(
        val text: String,
        val isError: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ChatMessage(
        val role: String, // "user" or "assistant"
        val message: String
    )

    init {
        // Prepopulate templates if DB is empty
        viewModelScope.launch {
            repository.allFiles.collect { files ->
                if (files.isEmpty()) {
                    prepopulateTemplates()
                } else {
                    // Load last active file, defaults to main.py
                    val file = files.firstOrNull { it.name == currentFileName } ?: files.firstOrNull()
                    if (file != null) {
                        currentFileName = file.name
                        currentFileContent = file.content
                    }
                }
            }
        }
    }

    private suspend fun prepopulateTemplates() {
        val templates = listOf(
            PythonFile(
                name = "main.py",
                content = """# שלום עולם בפייתון!
# מנוע python3 פועל ישירות בתוך הטלפון שלך.
# כאן תוכלו להריץ קוד, לכתוב סקריפטים ולייצר אלגוריתמים.

print("--- ברוכים הבאים ל-Python Studio ---")
print("פייתון אמיתי לחלוטין מורץ ישירות במכשיר הנייד.")

# נבצע חישוב קל
num1 = 824
num2 = 125
sum_result = num1 + num2
print(f"תוצאת החישוב: {num1} + {num2} = {sum_result}")

print("\nנסו לפתוח קבצי דגימה ומשחקים דרך סייר הקבצים למעלה!")
""",
                isFavorite = true
            ),
            PythonFile(
                name = "physics_game.py",
                content = """import js_canvas
import math
import time

# הגדרת ממשק קנווס ציור אינטראקטיבי בגודל 400 על 400
js_canvas.setSize(400, 400)

# משתני המשחק והפיזיקה
ball_x = 200.0
ball_y = 120.0
ball_dx = 5.0
ball_dy = 3.5
ball_radius = 12

paddle_x = 150
paddle_width = 110
paddle_height = 14
score = 0
lives = 3

print("--- התחלת משחק כדור מקפץ בקנווס! ---")
print("הוראות: געו במסך וגררו את האצבע ימינה ושמאלה כדי להפעיל את המחבט.")

def on_touch_start(x, y):
    global paddle_x
    paddle_x = int(x) - paddle_width // 2

def on_touch_move(x, y):
    global paddle_x
    paddle_x = int(x) - paddle_width // 2

# רישום מנהל המגע של המכשיר
js_canvas.register_touch(on_touch_start, on_touch_move, None)

# הרצת לולאת פריימים של תנועה (220 פריימים להדגמה)
for frame in range(220):
    # ניקוי מסך קנווס
    js_canvas.cls()
    
    # איור הרקע (מסך משחק כהה מודרני)
    js_canvas.rect(0, 0, 400, 400, "#13141c")
    
    # תנועת הכדור
    ball_x += ball_dx
    ball_y += ball_dy
    
    # התנגשות קירות צידיים
    if ball_x <= ball_radius or ball_x >= 400 - ball_radius:
        ball_dx = -ball_dx
        
    # התנגשות תקרה עליונה
    if ball_y <= ball_radius:
        ball_dy = -ball_dy
        
    # התנגשות עם המחבט
    if ball_y >= 350 - ball_radius and paddle_x <= ball_x <= paddle_x + paddle_width:
        ball_dy = -abs(ball_dy)
        score += 1
        print(f"פגיעה מוצלחת! ניקוד: {score}")
        
    # נפילה לתהום (איבוד חיים / פסילה)
    if ball_y > 400:
        lives -= 1
        print(f"נפילה! נותרו עוד {lives} חיים.")
        ball_x = 200.0
        ball_y = 120.0
        ball_dy = 3.5
        ball_dx = 4.0 if ball_dx > 0 else -4.0
        
        if lives <= 0:
            print("המשחק נגמר! החיים אזלו.")
            break
            
    # ציור המחבט בצבע ניאון מהמם
    js_canvas.rect(paddle_x, 350, paddle_width, paddle_height, "#00f0ff")
    
    # ציור הכדור
    js_canvas.circle(int(ball_x), int(ball_y), ball_radius, "#ffd700")
    
    # תצוגת ניקוד וחיים מעל
    js_canvas.text(f"SCORE: {score}", 15, 30, "bold 16px sans-serif", "#ffffff")
    js_canvas.text(f"LIVES: {lives}", 300, 30, "bold 16px sans-serif", "#ff4a4a")
    
    # המתנה של 35 מילישניות לקצב פריימים של כ-30FPS
    time.sleep(0.035)

js_canvas.cls()
js_canvas.rect(0, 0, 400, 400, "#1c1212")
js_canvas.text("GAME OVER", 110, 180, "bold 32px sans-serif", "#ff4444")
js_canvas.text(f"FINAL SCORE: {score}", 120, 230, "20px sans-serif", "#ffffff")
print(f"המשחק הסתיים בציון: {score}")
""",
                isFavorite = false
            ),
            PythonFile(
                name = "art_math.py",
                content = """import js_canvas
import math
import time

js_canvas.setSize(400, 400)
js_canvas.cls()
js_canvas.rect(0, 0, 400, 400, "#0a0a0f")

print("--- יוצר גרפיקה מקסימה מבוססת גישת עיצוב מתמטית ---")
print("הקוד מחשב קואורדינטות קווים במערכת סיבובית מתוזמנת...")

cx = 200
cy = 200

for i in range(150):
    angle = i * 0.13
    dist = i * 1.3
    
    x = cx + dist * math.cos(angle)
    y = cy + dist * math.sin(angle)
    
    # שינוי גוונים הדרגתי מהפנט
    r = int(128 + 127 * math.sin(angle))
    g = int(100 + 155 * (i / 150.0))
    b = int(255 * (1.0 - i / 150.0))
    
    color_code = f"#{r:02x}{g:02x}{b:02x}"
    
    # ציור קו מחבר מהמרכז
    js_canvas.line(cx, cy, int(x), int(y), f"rgba({r},{g},{b}, 0.1)", 1)
    
    # ציור נקודת פלאש קצה
    js_canvas.circle(int(x), int(y), int(2 + i/35.0), color_code, True)
    
    # חיבור בין נקודות עוקבות
    if i > 0:
        prev_a = (i-1) * 0.13
        prev_d = (i-1) * 1.3
        px = cx + prev_d * math.cos(prev_a)
        py = cy + prev_d * math.sin(prev_a)
        js_canvas.line(int(px), int(py), int(x), int(y), color_code, 2)
        
    if i % 4 == 0:
        time.sleep(0.01)

print("איור גיאומטרי מתמטי הושלם בהצלחה!")
""",
                isFavorite = false
            ),
            PythonFile(
                name = "plots.py",
                content = """# תרשים פונקציות מתמטי באמצעות NumPy ו-Matplotlib
# ספריות אלה טעונות מראש בתוך דפדפן הקנווס שלנו!

import numpy as np
import matplotlib.pyplot as plt

print("מחשב נקודות פונקציות גל של סינוס וקבלת תצוגה גרפית...")

# יצירת נתונים
x = np.linspace(0, 4 * np.pi, 200)
y1 = np.sin(x)
y2 = np.cos(x)

# בניית גרף מהפנט עם Matplotlib
plt.figure(figsize=(6, 4))
plt.plot(x, y1, label='Sin(x)', color='#00ffcc', linewidth=2)
plt.plot(x, y2, label='Cos(x)', color='#ff3366', linewidth=2, linestyle='--')

plt.title('Wave Functions inside PyStudio Client', color='#333333', fontsize=12)
plt.xlabel('Angles (radians)', color='#333333')
plt.ylabel('Amplitude', color='#333333')
plt.grid(True, linestyle=':', alpha=0.6)
plt.legend(loc='upper right')

# הצגת הגרף בתוך סביבת הקנווס המובנית
plt.show()

print("הגרף נוצר בהצלחה! עברו ללשונית 'קנווס ותלת מימד' כדי לצפות בגרף.")
""",
                isFavorite = false
            ),
            PythonFile(
                name = "interactive_input.py",
                content = """# פייתון תומך בקלט מתקדם ישירות מהממשק של האפליקציה!

print("אנא מלאו את הפרטים בחלונית הקלט שתפתח לכם במכשיר:")

name = input("מה השם שלך? ")
age = input("בן כמה אתה? ")

try:
    age_int = int(age)
    years_to_100 = 100 - age_int
    print(f"שלום {name}! נותרו לך עוד {years_to_100} שנים כדי להגיע לגיל 100!")
except ValueError:
    print(f"שלום {name}! הגיל שהקלדת '{age}' אינו מספר תקין.")
""",
                isFavorite = false
            )
        )
        for (template in templates) {
            repository.insertFile(template)
        }
        currentFileName = "main.py"
        currentFileContent = templates[0].content
    }

    // Load file structure
    fun selectFile(name: String) {
        viewModelScope.launch {
            val file = repository.getFileByName(name)
            if (file != null) {
                // Save current file contents before switching
                saveCurrentFile()
                currentFileName = file.name
                currentFileContent = file.content
            }
        }
    }

    fun saveCurrentFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = PythonFile(
                name = currentFileName,
                content = currentFileContent,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertFile(file)
        }
    }

    fun addNewFile(name: String) {
        if (name.isBlank()) return
        val sanitized = if (name.endsWith(".py")) name else "$name.py"
        viewModelScope.launch(Dispatchers.IO) {
            val newFile = PythonFile(
                name = sanitized,
                content = "# קובץ פייתון חדש: $sanitized\n\n",
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertFile(newFile)
            withContext(Dispatchers.Main) {
                selectFile(sanitized)
                isNewFileDialogOpen = false
            }
        }
    }

    fun deleteFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFileByName(name)
            withContext(Dispatchers.Main) {
                // Switch if deleted current
                if (currentFileName == name) {
                    val remaining = allFiles.value.firstOrNull { it.name != name }
                    if (remaining != null) {
                        currentFileName = remaining.name
                        currentFileContent = remaining.content
                    } else {
                        currentFileName = "main.py"
                        currentFileContent = ""
                    }
                }
            }
        }
    }

    // Engine feedback functions
    fun addLog(message: String, isError: Boolean = false) {
        val current = consoleLogs.toMutableList()
        current.add(ConsoleMessage(message, isError))
        consoleLogs = current
    }

    fun clearLogs() {
        consoleLogs = emptyList()
    }

    // Input prompt blocking synchronization
    fun handleInputRequest(prompt: String, continuation: (String) -> Unit) {
        inputRequestPrompt = prompt
        inputContinuation = continuation
    }

    fun submitInputResponse(resp: String) {
        inputContinuation?.invoke(resp)
        inputRequestPrompt = null
        inputContinuation = null
    }

    // Package Installation trigger
    fun requestInstallPackage(packageName: String) {
        if (packageName.isBlank()) return
        packageSearchQuery = ""
        isInstallingPackage = true
        installProgressText = "מתקין..."
    }

    fun finalizePackageInstall(packageName: String, success: Boolean) {
        isInstallingPackage = false
        if (success) {
            val current = installedPackages.toMutableList()
            if (!current.contains(packageName)) {
                current.add(packageName)
                installedPackages = current
            }
        }
    }

    // AI Copilot via Gemini
    fun sendChatMessage() {
        val msg = aiInputMessage.trim()
        if (msg.isEmpty()) return
        
        val updatedHistory = aiChatHistory.toMutableList()
        updatedHistory.add(ChatMessage("user", msg))
        aiChatHistory = updatedHistory
        aiInputMessage = ""
        isAiGenerating = true

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    delay(1500)
                    val reply = ChatMessage("assistant", "כדי להשתמש בבינה מלאכותית (Gemini Co-Pilot) לכתיבת קוד פייתון והסבר באגים, אנא הגדירו את מפתח ה-API שלכם (GEMINI_API_KEY) בלוח ה-Secrets של AI Studio במערכת.")
                    updatedHistory.add(reply)
                    aiChatHistory = updatedHistory
                } else {
                    val prompt = constructPrompt(updatedHistory)
                    val response = callGeminiApi(apiKey, prompt)
                    updatedHistory.add(ChatMessage("assistant", response))
                    aiChatHistory = updatedHistory
                }
            } catch (e: Exception) {
                updatedHistory.add(ChatMessage("assistant", "שגיאה בחיבור ל-Gemini API: ${e.message}"))
                aiChatHistory = updatedHistory
            } finally {
                isAiGenerating = false
            }
        }
    }

    private fun constructPrompt(history: List<ChatMessage>): String {
        val builder = StringBuilder()
        builder.append("You are PyStudio AI Companion, a brilliant Senior Python Software Engineer and Tutor. ")
        builder.append("You are helping the user build visual python games, scripts, and install libraries on Android. ")
        builder.append("Respond to the user in Hebrew. Keep code blocks neat, clear, and perfectly formatted using ```python. ")
        builder.append("When suggesting canvases drawings, remind the user they can use PyStudio's 'js_canvas' module with commands like setSize(400, 400), rect(x,y,w,h,color), circle(x,y,r,color), etc.\n\n")
        
        // Include last few context lines of history
        val context = history.takeLast(10)
        for (item in context) {
            val label = if (item.role == "user") "User" else "PyStudio AI"
            builder.append("$label: ${item.message}\n")
        }
        builder.append("PyStudio AI:")
        return builder.toString()
    }

    private suspend fun callGeminiApi(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "נכשל לתקשר עם יחידת הבינה המלאכותית: קוד שגיאה ${response.code}"
            }
            val rawResponse = response.body?.string() ?: ""
            try {
                val jsonResponse = JSONObject(rawResponse)
                val candidates = jsonResponse.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            } catch (e: Exception) {
                "שגיאה בעיבוד התשובה: ${e.message}\nתגובה גולמית: $rawResponse"
            }
        }
    }
}
