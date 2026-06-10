package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.ConsoleMessage as WebConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PythonWorkspaceViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val viewModel: PythonWorkspaceViewModel by viewModels()
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PyStudioApp(viewModel = viewModel, onInitWebView = { wV ->
                    webView = wV
                }, onRunCode = { code, filesJson ->
                    webView?.evaluateJavascript("runPythonCode(`${code.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")}`, `${filesJson}`)", null)
                }, onInstallPackage = { pkg ->
                    webView?.evaluateJavascript("installPackage('$pkg')", null)
                })
            }
        }
    }
}

// Custom Colors for Cyberpunk Tech Dark Theme
object PyColors {
    val DeepSlate = Color(0xFF0F1015)
    val PanelDark = Color(0xFF16171F)
    val BorderDark = Color(0xFF2E313D)
    val NeonTeal = Color(0xFF00FFCC)
    val LogoBlue = Color(0xFF457B9D)
    val CodeYellow = Color(0xFFFFD166)
    val ErrorRed = Color(0xFFFF5C5C)
    val CardBackground = Color(0xFF222531)
    val TextLight = Color(0xFFE2E4E9)
    val TextMuted = Color(0xFF9095A6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PyStudioApp(
    viewModel: PythonWorkspaceViewModel,
    onInitWebView: (WebView) -> Unit,
    onRunCode: (String, String) -> Unit,
    onInstallPackage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Editor, 1: Canvas, 2: Terminal, 3: Library, 4: AI

    // Auto-focus logic when running code
    fun triggerRun() {
        if (!viewModel.isEngineLoaded) {
            viewModel.addLog("מנוע הטעינה עדיין אינו מוכן. אנא המתן לסיום הטעינה...\n", true)
            return
        }
        viewModel.isRunning = true
        viewModel.clearLogs()
        viewModel.addLog("--- מתחיל הרצה... ---\n", false)
        
        // Save current code to database
        viewModel.saveCurrentFile()
        
        // Prepare list of other workspace files as JSON string to simulate modules/imports
        val filesArray = JSONArray()
        allFiles.forEach { file ->
            if (file.name != viewModel.currentFileName) {
                val fObj = JSONObject()
                fObj.put("name", file.name)
                fObj.put("content", file.content)
                filesArray.put(fObj)
            }
        }
        val filesJson = filesArray.toString().replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
        
        // Run code inside WebView
        onRunCode(viewModel.currentFileContent, filesJson)
        
        // Automatically switch to the Visual/Canvas output tab if running interactive graphic game or plotting script
        if (viewModel.currentFileName.contains("game") || viewModel.currentFileName.contains("art") || viewModel.currentFileName.contains("plot")) {
            selectedTab = 1
        } else {
            selectedTab = 2 // Switch to Console
        }
    }

    // Hidden webview initialized once and cached
    val mWebView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: WebConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val prefix = if (it.messageLevel() == WebConsoleMessage.MessageLevel.ERROR) "JS Error: " else "JS Console: "
                        android.util.Log.d("PyStudioWebView", "$prefix${it.message()}")
                    }
                    return true
                }
            }
            // Bind bridge
            addJavascriptInterface(object : Any() {
                @android.webkit.JavascriptInterface
                fun log(message: String, isError: Boolean) {
                    coroutineScope.launch {
                        viewModel.addLog(message, isError)
                    }
                }

                @android.webkit.JavascriptInterface
                fun updateStatus(status: String) {
                    coroutineScope.launch {
                        viewModel.engineStatus = status
                    }
                }

                @android.webkit.JavascriptInterface
                fun onEngineLoaded() {
                    coroutineScope.launch {
                        viewModel.isEngineLoaded = true
                        viewModel.engineStatus = "מוכן לשימוש"
                    }
                }

                @android.webkit.JavascriptInterface
                fun onExecutionCompleted(result: String) {
                    coroutineScope.launch {
                        viewModel.isRunning = false
                        viewModel.addLog(">>> תוכנית הסתיימה בהצלחה <<<", false)
                    }
                }

                @android.webkit.JavascriptInterface
                fun onExecutionFailed(error: String) {
                    coroutineScope.launch {
                        viewModel.isRunning = false
                        viewModel.addLog("\n[שגיאה בהרצה]: $error", true)
                        // Trigger AI Copilot automatic hint
                        viewModel.aiChatHistory = viewModel.aiChatHistory + PythonWorkspaceViewModel.ChatMessage(
                            "assistant",
                            "זיהיתי ששילבת שגיאת הרצה בקוד שלך:\n`$error`\nתרצה שאנתח אותה ואציע פתרון מהיר?"
                        )
                    }
                }

                @android.webkit.JavascriptInterface
                fun requestInput(prompt: String): String {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    var response = ""
                    coroutineScope.launch {
                        viewModel.handleInputRequest(prompt) { userInput ->
                            response = userInput
                            latch.countDown()
                        }
                    }
                    try {
                        latch.await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return response
                }

                @android.webkit.JavascriptInterface
                fun onPackageInstalled(name: String, success: Boolean, errorMsg: String) {
                    coroutineScope.launch {
                        viewModel.finalizePackageInstall(name, success)
                        if (success) {
                            viewModel.addLog("הספרייה '$name' הותקנה בהצלחה לפרוייקט!\n", false)
                        } else {
                            viewModel.addLog("שגיאה בהתקנת '$name': $errorMsg\n", true)
                        }
                    }
                }
            }, "AndroidBridge")

            loadUrl("file:///android_asset/pyodide_runner.html")
        }
    }

    LaunchedEffect(mWebView) {
        onInitWebView(mWebView)
    }

    // Trigger package managers installation evaluation
    LaunchedEffect(viewModel.isInstallingPackage) {
        if (viewModel.isInstallingPackage && viewModel.packageSearchQuery.isNotEmpty()) {
            onInstallPackage(viewModel.packageSearchQuery)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (viewModel.isEngineLoaded) PyColors.NeonTeal else PyColors.ErrorRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Python Studio",
                            style = TextStyle(
                                color = PyColors.TextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        )
                    }
                },
                actions = {
                    // Start run button
                    Button(
                        onClick = { triggerRun() },
                        enabled = !viewModel.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PyColors.NeonTeal,
                            contentColor = PyColors.DeepSlate
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("run_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Code",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("הפעל", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PyColors.DeepSlate,
                    titleContentColor = PyColors.TextLight
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PyColors.PanelDark,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val menuTabs = listOf(
                    Triple(0, "קוד", Icons.Default.Code),
                    Triple(1, "קנווס", Icons.Default.ArtTrack),
                    Triple(2, "מסוף", Icons.Default.Terminal),
                    Triple(3, "ספריות", Icons.Default.Dataset),
                    Triple(4, "הליפר AI", Icons.Default.Assistant)
                )
                menuTabs.forEach { (tabId, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == tabId,
                        onClick = { selectedTab = tabId },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selectedTab == tabId) PyColors.NeonTeal else PyColors.TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                color = if (selectedTab == tabId) PyColors.NeonTeal else PyColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == tabId) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = PyColors.BorderDark
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PyColors.DeepSlate)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Workspace info / status sub-header
                WorkspaceSubHeader(viewModel = viewModel)

                // Render matching tab screen layout
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> CodeEditorScreen(viewModel = viewModel)
                        1 -> CanvasOutputScreen(webView = mWebView)
                        2 -> TerminalLogsScreen(viewModel = viewModel)
                        3 -> LibraryPackageManagerScreen(viewModel = viewModel)
                        4 -> GeminiAssistantScreen(viewModel = viewModel)
                    }
                }
            }

            // Blocking Input Request Modal
            viewModel.inputRequestPrompt?.let { prompt ->
                var inputResponse by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("דרישת קלט פייתון", color = PyColors.TextLight) },
                    text = {
                        Column {
                            Text(prompt, color = PyColors.TextLight, modifier = Modifier.padding(bottom = 8.dp))
                            TextField(
                                value = inputResponse,
                                onValueChange = { inputResponse = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PyColors.NeonTeal,
                                    unfocusedBorderColor = PyColors.TextMuted,
                                    focusedTextColor = PyColors.TextLight,
                                    unfocusedTextColor = PyColors.TextLight
                                ),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_prompt_input_field")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.submitInputResponse(inputResponse) },
                            colors = ButtonDefaults.buttonColors(containerColor = PyColors.NeonTeal, contentColor = PyColors.DeepSlate)
                        ) {
                            Text("אישור")
                        }
                    },
                    containerColor = PyColors.PanelDark
                )
            }

            // Create Program Modal Dialog
            if (viewModel.isNewFileDialogOpen) {
                var newFileName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.isNewFileDialogOpen = false },
                    title = { Text("יצירת קובץ פייתון", color = PyColors.TextLight) },
                    text = {
                        Column {
                            Text("הכנס שם קובץ חדש (למשל sandbox.py):", color = PyColors.TextMuted)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                placeholder = { Text("script_new.py", color = PyColors.TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PyColors.NeonTeal,
                                    unfocusedBorderColor = PyColors.BorderDark,
                                    focusedTextColor = PyColors.TextLight,
                                    unfocusedTextColor = PyColors.TextLight
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_file_input_field")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.addNewFile(newFileName) },
                            colors = ButtonDefaults.buttonColors(containerColor = PyColors.NeonTeal, contentColor = PyColors.DeepSlate),
                            modifier = Modifier.testTag("new_file_confirm_button")
                        ) {
                            Text("צור קובץ")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.isNewFileDialogOpen = false }) {
                            Text("ביטול", color = PyColors.TextMuted)
                        }
                    },
                    containerColor = PyColors.PanelDark
                )
            }

            // Rename Program Modal Dialog
            if (viewModel.isRenameDialogOpen) {
                var renameValue by remember { mutableStateOf(viewModel.currentFileName) }
                AlertDialog(
                    onDismissRequest = { viewModel.isRenameDialogOpen = false },
                    title = { Text("שינוי שם קובץ פייתון", color = PyColors.TextLight) },
                    text = {
                        Column {
                            Text("הכניסו שם קובץ מעודכן משופר:", color = PyColors.TextMuted)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = renameValue,
                                onValueChange = { renameValue = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PyColors.NeonTeal,
                                    unfocusedBorderColor = PyColors.BorderDark,
                                    focusedTextColor = PyColors.TextLight,
                                    unfocusedTextColor = PyColors.TextLight
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rename_file_input_field")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.renameFile(viewModel.currentFileName, renameValue) },
                            colors = ButtonDefaults.buttonColors(containerColor = PyColors.NeonTeal, contentColor = PyColors.DeepSlate),
                            modifier = Modifier.testTag("rename_file_confirm_button")
                        ) {
                            Text("שנה שם")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.isRenameDialogOpen = false }) {
                            Text("ביטול", color = PyColors.TextMuted)
                        }
                    },
                    containerColor = PyColors.PanelDark
                )
            }
        }
    }
}

@Composable
fun WorkspaceSubHeader(viewModel: PythonWorkspaceViewModel) {
    val allFiles by viewModel.allFiles.collectAsState()
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PyColors.PanelDark)
            .border(1.dp, PyColors.BorderDark)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // File selection chooser dropdown
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showDropdown = true }
                    .background(PyColors.BorderDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File icon",
                    tint = PyColors.CodeYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = viewModel.currentFileName,
                    color = PyColors.TextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown icon",
                    tint = PyColors.TextMuted
                )
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier.background(PyColors.PanelDark)
            ) {
                allFiles.forEach { file ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (file.isFavorite) Icons.Default.Star else Icons.Default.InsertDriveFile,
                                    tint = if (file.isFavorite) PyColors.CodeYellow else PyColors.TextMuted,
                                    contentDescription = "",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(file.name, color = PyColors.TextLight)
                            }
                        },
                        onClick = {
                            viewModel.selectFile(file.name)
                            showDropdown = false
                        }
                    )
                }
            }
        }

        Row {
            // Rename quickbutton
            IconButton(
                onClick = { viewModel.isRenameDialogOpen = true },
                modifier = Modifier.testTag("nav_rename_file_icon_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename File",
                    tint = PyColors.CodeYellow,
                    modifier = Modifier.size(18.dp)
                )
            }

            // New File quickbutton
            IconButton(
                onClick = { viewModel.isNewFileDialogOpen = true },
                modifier = Modifier.testTag("nav_new_file_icon_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New File",
                    tint = PyColors.NeonTeal,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete current file if not main.py
            if (viewModel.currentFileName != "main.py") {
                IconButton(
                    onClick = { viewModel.deleteFile(viewModel.currentFileName) },
                    modifier = Modifier.testTag("nav_delete_file_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete File",
                        tint = PyColors.ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Custom Python simple syntax linter
fun validatePythonSyntax(code: String): String? {
    val lines = code.split("\n")
    val colonsKeywords = listOf("def", "for", "while", "if", "elif", "else", "try", "except", "class")
    for (i in lines.indices) {
        val trimmed = lines[i].trim()
        if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
        
        // Check for missing colons
        for (kw in colonsKeywords) {
            if (trimmed.startsWith("$kw ") || trimmed.startsWith("$kw:") || trimmed == kw) {
                if (!trimmed.endsWith(":")) {
                    if (!trimmed.endsWith("\\") && !trimmed.contains("import")) {
                        return "שורה ${i + 1}: חסר נקודתיים (:) בסוף השורה של ה-$kw"
                    }
                }
            }
        }
        
        // Check for unmatched parentheses or brackets
        var openParen = 0
        var openBrack = 0
        var openCurly = 0
        for (char in trimmed) {
            if (char == '(') openParen++
            if (char == ')') openParen--
            if (char == '[') openBrack++
            if (char == ']') openBrack--
            if (char == '{') openCurly++
            if (char == '}') openCurly--
        }
        if (openParen != 0) return "שורה ${i + 1}: סוגריים עגולים ( ) לא סגורים כראוי"
        if (openBrack != 0) return "שורה ${i + 1}: סוגריים מרובעים [ ] לא סגורים כראוי"
        if (openCurly != 0) return "שורה ${i + 1}: סוגריים מסולסלים { } לא סגורים כראוי"
    }
    return null
}

@Composable
fun RecipeItem(title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PyColors.BorderDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, PyColors.BorderDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = PyColors.NeonTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = PyColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun CodeEditorScreen(viewModel: PythonWorkspaceViewModel) {
    var contentState by remember(viewModel.currentFileName) {
        mutableStateOf(viewModel.currentFileContent)
    }

    // Capture user changes reactively
    LaunchedEffect(contentState) {
        viewModel.currentFileContent = contentState
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PyColors.DeepSlate)
    ) {
        // Advanced Toolbar Row (Linter & Sharing / Utility Hub)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Linter status indicator
            val syntaxError = remember(contentState) { validatePythonSyntax(contentState) }
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (syntaxError == null) Color(0xFF4CAF50) else Color(0xFFFFC107),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = syntaxError ?: "תקינות סינטקס: הקוד מנוסח כראוי",
                    color = if (syntaxError == null) Color(0xFF81C784) else Color(0xFFFFD54F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Quick Actions Hub
            val context = LocalContext.current
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            var showRecipeDialog by remember { mutableStateOf(false) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Snippet Recipe Shortcut
                TextButton(
                    onClick = { showRecipeDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = PyColors.NeonTeal)
                ) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = "Snippets", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("קודים מוכנים", fontSize = 11.sp)
                }

                // Copy
                IconButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(contentState))
                        android.widget.Toast.makeText(context, "הקוד הועתק ללוח בהצלחה", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = PyColors.TextMuted, modifier = Modifier.size(15.dp))
                }

                // Share
                IconButton(
                    onClick = {
                        try {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, contentState)
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "שתף סקריפט פייתון")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "שגיאה בשיתוף הקובץ", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = PyColors.TextMuted, modifier = Modifier.size(15.dp))
                }
            }

            if (showRecipeDialog) {
                AlertDialog(
                    onDismissRequest = { showRecipeDialog = false },
                    title = { Text("מאגר מתכוני קוד מוכנים לפייתון", color = PyColors.TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text("בחרו סניפט קוד להזרקה מיידית למסך העריכה:", color = PyColors.TextMuted, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Recipe 1 - Canvas Loop and math Art
                            RecipeItem(
                                title = "ציור מעגל צבעוני ומתמטי",
                                desc = "פקודות קנווס פשוטות משולבות פונקציות טריגונומטריות (math.sin)",
                                onClick = {
                                    contentState = """import js_canvas
import math
import time

js_canvas.setSize(400, 400)
js_canvas.cls()
js_canvas.rect(0, 0, 400, 400, "#080b10")

print("מריץ ציור כוכב ציקלואידי...")
cx = 200
cy = 200

for i in range(120):
    t = i * 0.15
    # חישוב רדיוס משתנה
    r = 100 + 40 * math.sin(t * 5)
    x = cx + r * math.cos(t)
    y = cy + r * math.sin(t)
    
    js_canvas.circle(int(x), int(y), 4, "#ff007f" if i % 2 == 0 else "#00f3ff")
    time.sleep(0.02)

print("הציור המרהיב הושלם!")
"""
                                    showRecipeDialog = false
                                }
                            )

                            // Recipe 2 - SymPy Calculation
                            RecipeItem(
                                title = "אלגברה סימבולית ונגזרות (SymPy)",
                                desc = "חישוב נגזרת לפונקציה מתמטית sympy באופן דינמי",
                                onClick = {
                                    contentState = """import sympy
print("מתחיל חישוב אלגברי מתקדם...")
x = sympy.symbols('x')
f = x**3 + 2*x**2 - 5*x + 10
f_prime = sympy.diff(f, x)

print(f"הפונקציה המקורית: f(x) = {f}")
print(f"הנגזרת הראשונה: f'(x) = {f_prime}")
print(f"ערך הנגזרת בנקודה x=2 הוא: {f_prime.subs(x, 2)}")
"""
                                    showRecipeDialog = false
                                }
                            )

                            // Recipe 3 - NumPy stats
                            RecipeItem(
                                title = "ניתוח סטטיסטי בעזרת NumPy",
                                desc = "חישוב ממוצע, חציון וסטיית תקן של מערך מספרים",
                                onClick = {
                                    contentState = """import numpy as np

# הגדרת נתוני דגימה של ציוני כיתה
scores = np.array([85, 92, 78, 90, 88, 76, 95, 89, 100, 82])

print("--- דוח אנליזה סטטיסטית ---")
print(f"ציונים: {scores}")
print(f"ממוצע ציוני הכיתה: {np.mean(scores):.2f}")
print(f"חציון: {np.median(scores)}")
print(f"סטיית תקן: {np.std(scores):.2f}")
print(f"הציון המקסימלי: {np.max(scores)}")
print(f"הציון המינימלי: {np.min(scores)}")
"""
                                    showRecipeDialog = false
                                }
                            )

                            // Recipe 4 - User Inputs & JSON parse
                            RecipeItem(
                                title = "קלט משתמש קלאסי והמרות JSON",
                                desc = "שימוש בפונקציית input() הדינמית ופענוח JSON מובנה",
                                onClick = {
                                    contentState = """import json

print("ברוכים הבאים למערכת רישום המוצרים בפייתון!")
name = input("הקלידו שם מוצר חדש: ")
price = input("הקלידו את מחיר המוצר בשקלים: ")

try:
    numeric_price = float(price)
    discount = numeric_price * 0.9  # 10% הנחה
    
    # יצירת מילון והמרה ל-JSON string
    product_dict = {
        "item_name": name,
        "original_price": numeric_price,
        "discounted_price": discount
    }
    
    json_data = json.dumps(product_dict, ensure_ascii=False, indent=4)
    print("\n--- יצירת נתוני JSON מובנים ---")
    print(json_data)
except ValueError:
    print("שגיאה: המחיר שהקלדתם אינו מספר תקין.")
"""
                                    showRecipeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRecipeDialog = false }) {
                            Text("סגור", color = PyColors.NeonTeal)
                        }
                    },
                    containerColor = PyColors.PanelDark
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // High-Contrast Monospaced Editor Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(8.dp)
                    .border(1.dp, PyColors.BorderDark, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(PyColors.PanelDark)
            ) {
                OutlinedTextField(
                    value = contentState,
                    onValueChange = { contentState = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = PyColors.TextLight,
                        lineHeight = 20.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = PyColors.TextLight,
                        unfocusedTextColor = PyColors.TextLight
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("editor_text_field"),
                    placeholder = {
                        Text(
                            "# התחילו לכתוב קוד פייתון כאן...",
                            color = PyColors.TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        // Quick Accessory Typing Keyboard Helper Toolbar Row
        QuickAccessoryToolbar(onInsertChar = { char ->
            val updated = contentState + char
            contentState = updated
        })
    }
}

@Composable
fun QuickAccessoryToolbar(onInsertChar: (String) -> Unit) {
    val characters = listOf(
        "    " to "Tab",
        ":" to ":",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "\"" to "\"",
        "'" to "'",
        "_" to "_",
        "=" to "=",
        "+" to "+",
        "-" to "-",
        "." to "."
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(PyColors.PanelDark)
            .border(1.dp, PyColors.BorderDark)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        characters.forEach { (insertedValue, displayLabel) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .border(1.dp, PyColors.BorderDark, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onInsertChar(insertedValue) }
                    .background(PyColors.CardBackground)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayLabel,
                    color = PyColors.NeonTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CanvasOutputScreen(webView: WebView) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PyColors.DeepSlate)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Embedded Android View containing Pyodide engine (WebView Canvas)
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, PyColors.BorderDark, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .testTag("embedded_game_webview")
        )
    }
}

@Composable
fun TerminalLogsScreen(viewModel: PythonWorkspaceViewModel) {
    val listState = rememberLazyListState()

    // Auto-scroll to lowest console log on new message
    LaunchedEffect(viewModel.consoleLogs.size) {
        if (viewModel.consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.consoleLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "פלט מסוף ותקלות:",
                color = PyColors.TextLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            TextButton(
                onClick = { viewModel.clearLogs() },
                colors = ButtonDefaults.textButtonColors(contentColor = PyColors.ErrorRed)
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = "", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("נקה הכל", fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .border(1.dp, PyColors.BorderDark, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(PyColors.PanelDark)
                .padding(12.dp)
        ) {
            if (viewModel.consoleLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "",
                            tint = PyColors.BorderDark,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "המסוף ריק כעת\nהקלק על 'הפעל' כדי להקצות קוד פייתון",
                            color = PyColors.TextMuted,
                            fontSize = 12.sp,
                            style = TextStyle(lineHeight = 18.sp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.consoleLogs) { log ->
                        Text(
                            text = log.text,
                            color = if (log.isError) PyColors.ErrorRed else PyColors.TextLight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryPackageManagerScreen(viewModel: PythonWorkspaceViewModel) {
    var query by remember { mutableStateOf("") }
    
    val quickRecommendations = listOf(
        "cowsay" to "יוצר ציורי פרה ASCII מגניבים",
        "requests" to "תקשורת אינטרנט ושאילתות HTTP",
        "sympy" to "חישובי אלגברה סימבוליים מתקדמים",
        "beautifulsoup4" to "ניתוח ואחזור נתוני דפי אינטרנט HTML",
        "numpy" to "ספריית מטריצות ומתמטיקה טעונה מראש",
        "matplotlib" to "סרטוט גרפים ודיאגרמות טעונה מראש"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "מנהל ספריות פייתון (PIP / Micropip)",
            color = PyColors.TextLight,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "התקינו חבילות ספריות אמיתיות מ-PyPI ישירות לפלייגראונד שלכם בקליק!",
            color = PyColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Custom search field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("הכניסו שם חבילה להתקנה, למשל cowsay...", color = PyColors.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PyColors.NeonTeal,
                unfocusedBorderColor = PyColors.BorderDark,
                focusedTextColor = PyColors.TextLight,
                unfocusedTextColor = PyColors.TextLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_field"),
            singleLine = true,
            trailingIcon = {
                if (viewModel.isInstallingPackage) {
                    CircularProgressIndicator(color = PyColors.NeonTeal, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        if (query.isNotBlank()) {
                            viewModel.requestInstallPackage(query)
                            query = ""
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "", tint = PyColors.NeonTeal)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preloaded packages status board
        Text(
            text = "חבילות וספריות שגרתיות במנוע:",
            color = PyColors.TextLight,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.installedPackages.forEach { pkg ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PyColors.BorderDark)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = "✓ $pkg", color = PyColors.NeonTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = PyColors.BorderDark)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ספריות פופולריות מומלצות:",
                    color = PyColors.TextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(quickRecommendations) { (pkgName, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, PyColors.BorderDark, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(PyColors.PanelDark)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = pkgName, color = PyColors.TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = desc, color = PyColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Button(
                        onClick = { viewModel.requestInstallPackage(pkgName) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PyColors.BorderDark,
                            contentColor = PyColors.NeonTeal
                        ),
                        modifier = Modifier.testTag("install_$pkgName")
                    ) {
                        Text("התקן", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiAssistantScreen(viewModel: PythonWorkspaceViewModel) {
    val listState = rememberLazyListState()

    // Scroll chat bottom on change
    LaunchedEffect(viewModel.aiChatHistory.size) {
        if (viewModel.aiChatHistory.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.aiChatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Assistant, contentDescription = "", tint = PyColors.NeonTeal, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "עוזר הפייתון האינטליגנטי (Gemini Pro)",
                color = PyColors.TextLight,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Text(
            text = "AI מפענח שגיאות, מציע קטעי קוד פייתון מהממים ומדגים ציורי קנווס שלמים!",
            color = PyColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Chat lists scroll area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(vertical = 8.dp)
                .border(1.dp, PyColors.BorderDark, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(PyColors.PanelDark)
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.aiChatHistory) { chatMessage ->
                    val isAssistant = chatMessage.role == "assistant"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = if (isAssistant) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isAssistant) 0.dp else 12.dp,
                                        bottomEnd = if (isAssistant) 12.dp else 0.dp
                                    )
                                )
                                .background(if (isAssistant) PyColors.CardBackground else PyColors.LogoBlue)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = chatMessage.message,
                                color = PyColors.TextLight,
                                fontSize = 13.sp,
                                style = TextStyle(lineHeight = 18.sp)
                            )
                        }
                    }
                }
                if (viewModel.isAiGenerating) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(PyColors.CardBackground, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = PyColors.NeonTeal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("מנתח את בקשתך כעת...", color = PyColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bottom text sender box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.aiInputMessage,
                onValueChange = { viewModel.aiInputMessage = it },
                keyboardActions = KeyboardActions(onSend = { viewModel.sendChatMessage() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                placeholder = { Text("שאלו למשל: איך ליצור כדור זז בקנווס?", color = PyColors.TextMuted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PyColors.NeonTeal,
                    unfocusedBorderColor = PyColors.BorderDark,
                    focusedTextColor = PyColors.TextLight,
                    unfocusedTextColor = PyColors.TextLight
                ),
                textStyle = TextStyle(fontSize = 13.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_input_field"),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.sendChatMessage() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PyColors.NeonTeal)
                    .size(46.dp)
                    .testTag("send_ai_message_button")
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = PyColors.DeepSlate)
            }
        }
    }
}
