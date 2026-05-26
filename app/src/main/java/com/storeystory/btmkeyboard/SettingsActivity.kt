package com.storeystory.btmkeyboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import android.content.Context

import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.ui.graphics.toArgb

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(KeyboardTheme.load(this)) }
            val context = LocalContext.current
            
            val appThemeMode by remember { 
                mutableStateOf(context.getSharedPreferences("kb_prefs", Context.MODE_PRIVATE).getString("app_theme", "System") ?: "System")
            }
            var currentAppTheme by remember { mutableStateOf(appThemeMode) }

            val darkTheme = when (currentAppTheme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "Bottom Keyboard Settings",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }

                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("App Theme", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("System", "Light", "Dark").forEach { mode ->
                                        FilterChip(
                                            selected = currentAppTheme == mode,
                                            onClick = {
                                                currentAppTheme = mode
                                                context.getSharedPreferences("kb_prefs", Context.MODE_PRIVATE).edit().putString("app_theme", mode).apply()
                                            },
                                            label = { Text(mode) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                }) { Text("1. Enable") }
                                
                                Button(onClick = {
                                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.showInputMethodPicker()
                                }) { Text("2. Select") }
                            }
                        }

                        item {
                            Button(onClick = {
                                if (!Settings.canDrawOverlays(this@SettingsActivity)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(intent)
                                }
                            }) { Text("3. Grant Overlay Permission") }
                        }

                        item { HorizontalDivider() }

                        item { Text("Theme Customization", style = MaterialTheme.typography.titleLarge) }

                        item {
                            ColorPickerRow("Background Color", theme.backgroundColor) { 
                                theme = theme.copy(backgroundColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                uri?.let {
                                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    theme = theme.copy(backgroundImageUri = it.toString()).also { t -> t.save(context) }
                                }
                            }
                            Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                                Text("Pick Background Image")
                            }
                        }

                        item {
                            ColorPickerRow("Text Color", theme.textColor) { 
                                theme = theme.copy(textColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            ColorPickerRow("Button Color", theme.buttonColor) { 
                                theme = theme.copy(buttonColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            ColorPickerRow("Border Color", theme.borderColor) { 
                                theme = theme.copy(borderColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            ColorPickerRow("Selector Border", theme.selectorColor) { 
                                theme = theme.copy(selectorColor = it).also { t -> t.save(context) }
                            }
                        }
                        
                        item {
                            ColorPickerRow("Selector Text", theme.selectorTextColor) { 
                                theme = theme.copy(selectorTextColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            ColorPickerRow("Selector BG", theme.selectorBackgroundColor) { 
                                theme = theme.copy(selectorBackgroundColor = it).also { t -> t.save(context) }
                            }
                        }

                        item {
                            Text("Vibration Intensity", style = MaterialTheme.typography.bodyMedium)
                            var intensity by remember { mutableStateOf(context.getSharedPreferences("kb_theme", Context.MODE_PRIVATE).getInt("vibrate_intensity", 100).toFloat()) }
                            Slider(
                                value = intensity,
                                onValueChange = { 
                                    intensity = it
                                    context.getSharedPreferences("kb_theme", Context.MODE_PRIVATE).edit().putInt("vibrate_intensity", it.toInt()).apply()
                                },
                                valueRange = 0f..255f
                            )
                        }

                        item {
                            Button(onClick = {
                                theme = KeyboardTheme().also { it.save(context) }
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Reset to Default")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ColorPickerRow(label: String, currentColor: Color, onColorSelected: (Color) -> Unit) {
        val colors = listOf(Color.Black, Color.White, Color.Gray, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan, Color.Transparent)
        var hexText by remember(currentColor) { 
            mutableStateOf(String.format("#%08X", currentColor.toArgb())) 
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color, CircleShape)
                            .border(2.dp, if (color == currentColor) Color.Black else Color.LightGray, CircleShape)
                            .clickable { onColorSelected(color) }
                    )
                }
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { 
                    hexText = it
                    if (it.length >= 7) {
                        try {
                            val parsedColor = Color(android.graphics.Color.parseColor(it))
                            onColorSelected(parsedColor)
                        } catch (e: Exception) {}
                    }
                },
                label = { Text("Hex Code (#AARRGGBB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
