package com.storeystory.btmkeyboard

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class KeyboardTheme(
    val backgroundColor: Color = Color.Black,
    val backgroundImageUri: String? = null,
    val textColor: Color = Color.White,
    val buttonColor: Color = Color(0xFF333333),
    val borderColor: Color = Color.Transparent,
    val selectorColor: Color = Color.Yellow,
    val selectorTextColor: Color = Color.Black,
    val selectorBackgroundColor: Color = Color.White
) {
    companion object {
        fun load(context: Context): KeyboardTheme {
            val prefs = context.getSharedPreferences("kb_theme", Context.MODE_PRIVATE)
            return KeyboardTheme(
                backgroundColor = Color(prefs.getInt("bg_color", Color.Black.toArgb())),
                backgroundImageUri = prefs.getString("bg_image", null),
                textColor = Color(prefs.getInt("text_color", Color.White.toArgb())),
                buttonColor = Color(prefs.getInt("btn_color", Color(0xFF333333).toArgb())),
                borderColor = Color(prefs.getInt("border_color", Color.Transparent.toArgb())),
                selectorColor = Color(prefs.getInt("sel_color", Color.Yellow.toArgb())),
                selectorTextColor = Color(prefs.getInt("sel_text_color", Color.Black.toArgb())),
                selectorBackgroundColor = Color(prefs.getInt("sel_bg_color", Color.White.toArgb()))
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("kb_theme", Context.MODE_PRIVATE).edit().apply {
            putInt("bg_color", backgroundColor.toArgb())
            putString("bg_image", backgroundImageUri)
            putInt("text_color", textColor.toArgb())
            putInt("btn_color", buttonColor.toArgb())
            putInt("border_color", borderColor.toArgb())
            putInt("sel_color", selectorColor.toArgb())
            putInt("sel_text_color", selectorTextColor.toArgb())
            putInt("sel_bg_color", selectorBackgroundColor.toArgb())
            apply()
        }
    }
}
