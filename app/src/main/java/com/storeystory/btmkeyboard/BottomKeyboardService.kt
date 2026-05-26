package com.storeystory.btmkeyboard

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage

class BottomKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var currentDisplayId = -1
    private var vibrator: Vibrator? = null
    private var theme by mutableStateOf(KeyboardTheme())

    // Keyboard Layouts
    private val baseRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m", "⌫"),
        listOf("SYM", "SPACE", "ENTER")
    )

    private val symbolsRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
        listOf("TAB", "ESC", "ABC", "SPACE", "ENTER")
    )

    private val rows: List<List<String>>
        get() = if (isSymbolsMode) {
            symbolsRows
        } else if (isUpperCase) {
            baseRows.map { row -> row.map { key -> if (key.length == 1 && key != "⌫") key.uppercase() else key } }
        } else {
            baseRows
        }

    // State
    private var selectedRow by mutableStateOf(0)
    private var selectedCol by mutableStateOf(0)
    private var isUpperCase by mutableStateOf(true)
    private var isCapsLock by mutableStateOf(false)
    private var isSymbolsMode by mutableStateOf(false)
    private var isCursorMode by mutableStateOf(false)
    private var connectionDebug by mutableStateOf("Ready")
    private var lastSpaceTime = 0L

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val prefs = getSharedPreferences("kb_prefs", Context.MODE_PRIVATE)
        currentDisplayId = prefs.getInt("display_id", -1)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        theme = KeyboardTheme.load(this)
    }

    override fun onCreateInputView(): View {
        val emptyView = View(this)
        emptyView.layoutParams = FrameLayout.LayoutParams(0, 0)
        return emptyView
    }

    override fun onWindowShown() {
        super.onWindowShown()
        theme = KeyboardTheme.load(this)
        updateAutoCaps()
        showOverlay()
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateAutoCaps()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
    }

    private fun updateAutoCaps() {
        if (isCapsLock || isSymbolsMode) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)
        isUpperCase = if (before == null || before.isEmpty()) {
            true
        } else {
            val text = before.toString()
            text.endsWith(". ") || text.endsWith("? ") || text.endsWith("! ") || text.endsWith("\n")
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        hideOverlay()
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = if (currentDisplayId != -1) {
            displayManager.displays.find { it.displayId == currentDisplayId }
        } else {
            displayManager.displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        } ?: displayManager.displays.firstOrNull()

        if (targetDisplay != null) {
            currentDisplayId = targetDisplay.displayId
            val displayContext = createDisplayContext(targetDisplay)
            windowManager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            targetDisplay.getRealMetrics(metrics)

            val params = WindowManager.LayoutParams(
                metrics.widthPixels,
                metrics.heightPixels,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val composeView = ComposeView(displayContext).apply {
                setContent {
                    MaterialTheme {
                        Surface(modifier = Modifier.fillMaxSize(), color = theme.backgroundColor) {
                            theme.backgroundImageUri?.let { uriString ->
                                AsyncImage(
                                    model = Uri.parse(uriString),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            KeyboardUI()
                        }
                    }
                }
            }

            composeView.setViewTreeLifecycleOwner(this)
            composeView.setViewTreeViewModelStoreOwner(this)
            composeView.setViewTreeSavedStateRegistryOwner(this)

            overlayView = composeView
            try {
                windowManager?.addView(overlayView, params)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            } catch (e: Exception) {
                connectionDebug = "Error: ${e.message}"
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        connectionDebug = "Key: $keyCode"
        
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            if (event.repeatCount == 0) {
                isCursorMode = !isCursorMode
                vibrateStrong()
            }
            return true
        }

        // Handle Backspace Repeat Delay
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            if (event.repeatCount > 0 && event.repeatCount % 5 != 0) return true 
        } else if (event.repeatCount > 0) {
            if (keyCode !in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) return true
        }

        if (isCursorMode) {
            val ic = currentInputConnection
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)); ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)); ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)); ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)); ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)); return true }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                selectedRow = if (selectedRow > 0) selectedRow - 1 else rows.size - 1
                if (selectedCol >= rows[selectedRow].size) selectedCol = rows[selectedRow].size - 1
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selectedRow = if (selectedRow < rows.size - 1) selectedRow + 1 else 0
                if (selectedCol >= rows[selectedRow].size) selectedCol = rows[selectedRow].size - 1
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selectedCol = if (selectedCol > 0) selectedCol - 1 else rows[selectedRow].size - 1
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selectedCol = if (selectedCol < rows[selectedRow].size - 1) selectedCol + 1 else 0
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1 -> {
                handleKey(rows[selectedRow][selectedCol])
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (event.repeatCount == 0) { isCapsLock = !isCapsLock; isUpperCase = isCapsLock; vibrateStrong() }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_L1 -> { handleKey("⌫"); return true }
            KeyEvent.KEYCODE_BUTTON_X -> {
                val now = System.currentTimeMillis()
                if (now - lastSpaceTime < 300) {
                    val ic = currentInputConnection
                    ic?.deleteSurroundingText(1, 0)
                    handleKey(".")
                    handleKey(" ")
                    lastSpaceTime = 0
                } else {
                    handleKey(" ")
                    lastSpaceTime = now
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (isSymbolsMode) isSymbolsMode = false else { isUpperCase = !isUpperCase; isCapsLock = false }
                return true 
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                isSymbolsMode = !isSymbolsMode
                vibrateStrong()
                return true
            }
            // L3 for Enter
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                handleKey("ENTER")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun vibrateLight() {
        vibrator?.let {
            val intensity = getSharedPreferences("kb_theme", Context.MODE_PRIVATE).getInt("vibrate_intensity", 100)
            if (intensity == 0) return@let
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(20, (intensity * 1.5).toInt().coerceIn(1, 255)))
            } else { @Suppress("DEPRECATION") it.vibrate(20) }
        }
    }

    private fun vibrateStrong() {
        vibrator?.let {
            val intensity = getSharedPreferences("kb_theme", Context.MODE_PRIVATE).getInt("vibrate_intensity", 100)
            if (intensity == 0) return@let
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(60, (intensity * 2.5).toInt().coerceIn(1, 255)))
            } else { @Suppress("DEPRECATION") it.vibrate(60) }
        }
    }

    @Composable
    fun KeyboardUI() {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val modeText = if (isCursorMode) "CURSOR" else if (isSymbolsMode) "SYMBOLS" else "INSERT"
            val capsText = if (isCapsLock) " [CAPS]" else ""
            Text(
                text = "MODE: $modeText$capsText | L3: Enter | R3: Sym",
                style = MaterialTheme.typography.labelSmall,
                color = if (isCursorMode) theme.selectorColor else theme.textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEachIndexed { colIndex, key ->
                        val isSelected = rowIndex == selectedRow && colIndex == selectedCol
                        KeyButton(text = key, modifier = Modifier.weight(if (key == "SPACE") 2f else 1f).fillMaxSize(), isSelected = isSelected)
                    }
                }
            }
        }
    }

    @Composable
    fun KeyButton(text: String, modifier: Modifier = Modifier, isSelected: Boolean) {
        Box(
            modifier = modifier.padding(1.dp).background(color = if (isSelected) theme.selectorBackgroundColor else theme.buttonColor, shape = RoundedCornerShape(4.dp))
                .border(width = 2.dp, color = if (isSelected) theme.selectorColor else theme.borderColor, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) theme.selectorTextColor else theme.textColor)
        }
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        vibrateLight()
        when (key) {
            "SYM" -> isSymbolsMode = true
            "ABC" -> { isSymbolsMode = false; isUpperCase = true }
            "TAB" -> { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB)) }
            "ESC" -> { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE)) }
            "⌫" -> {
                val before = ic.getTextBeforeCursor(1, 0)
                if (before == null || before.isEmpty()) {
                    requestHideSelf(0)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    updateAutoCaps()
                }
            }
            "ENTER" -> { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)); if (!isCapsLock) isUpperCase = true }
            "SPACE", " " -> { ic.commitText(" ", 1); updateAutoCaps() }
            else -> {
                ic.commitText(key, 1)
                if (!isCapsLock && key.length == 1) isUpperCase = false
                if (key == "." || key == "?" || key == "!") updateAutoCaps()
            }
        }
    }
}
