package com.hoba.assistant

import android.content.Context

/** تنظيف النص العربي عشان المقارنة تبقى سهلة (تشكيل، همزات، ياء/ألف مقصورة، تاء مربوطة). */
object Norm {
    private val marks = Regex("[\\u064B-\\u0652\\u0640]")
    private val spaces = Regex("\\s+")
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun norm(s: String): String = s.lowercase()
        .replace(marks, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(spaces, " ")
        .trim()

    fun latinDigits(s: String): String = buildString {
        for (c in s) {
            val i = ARABIC_DIGITS.indexOf(c)
            append(if (i >= 0) ('0' + i) else c)
        }
    }

    private val words: Map<String, Int> = mapOf(
        "واحد" to 1, "واحده" to 1, "اتنين" to 2, "اثنين" to 2, "تلاته" to 3, "ثلاثه" to 3,
        "اربعه" to 4, "خمسه" to 5, "سته" to 6, "سبعه" to 7, "تمانيه" to 8, "ثمانيه" to 8,
        "تسعه" to 9, "عشره" to 10,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    fun number(s: String): Int? {
        val t = latinDigits(norm(s))
        return t.toIntOrNull() ?: words[t]
    }
}

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("hoba", Context.MODE_PRIVATE)
    fun language(c: Context): String = p(c).getString("lang", "ar-EG") ?: "ar-EG"
    fun setLanguage(c: Context, v: String) {
        p(c).edit().putString("lang", v).apply()
    }
}

sealed class Cmd {
    object Back : Cmd()
    object Home : Cmd()
    object Recents : Cmd()
    object Notifications : Cmd()
    object QuickSettings : Cmd()
    object ScrollDown : Cmd()
    object ScrollUp : Cmd()
    object Screenshot : Cmd()
    object Lock : Cmd()
    object PowerMenu : Cmd()
    object VolUp : Cmd()
    object VolDown : Cmd()
    object ShowNumbers : Cmd()
    object HideNumbers : Cmd()
    object ClearText : Cmd()
    object Stop : Cmd()
    class OpenApp(val name: String) : Cmd()
    class Click(val target: String) : Cmd()
    class ClickNumber(val n: Int) : Cmd()
    class Type(val text: String) : Cmd()
    class Call(val name: String) : Cmd()
}

/** بيحوّل الجملة اللي اتقالت لأمر. بيفهم عربي مصري وإنجليزي. */
object Parser {
    private class Rest(val raw: String, val norm: String)

    private fun exact(t: String, vararg keys: String): Boolean = keys.any { Norm.norm(it) == t }

    /** لو الجملة بتبدأ بواحدة من الكلمات (الأطول الأول) بيرجع الباقي بعدها. */
    private fun rest(raw: String, vararg keys: String): Rest? {
        val words = raw.trim().split(Regex("\\s+"))
        val nw = words.map { Norm.norm(it) }
        for (k in keys) {
            val kw = Norm.norm(k).split(" ")
            if (nw.size > kw.size && nw.subList(0, kw.size) == kw) {
                val r = words.drop(kw.size).joinToString(" ")
                return Rest(r, Norm.norm(r))
            }
        }
        return null
    }

    fun parse(raw: String): Cmd? {
        val t = Norm.norm(raw)
        if (t.isEmpty()) return null

        when {
            exact(t, "ارجع", "رجوع", "ارجع لورا", "back", "go back") -> return Cmd.Back
            exact(t, "الرئيسية", "الصفحة الرئيسية", "روح الرئيسية", "هوم", "home", "go home") -> return Cmd.Home
            exact(t, "التطبيقات الاخيرة", "اخر التطبيقات", "recents", "recent apps") -> return Cmd.Recents
            exact(t, "الاشعارات", "افتح الاشعارات", "notifications") -> return Cmd.Notifications
            exact(t, "الاعدادات السريعة", "quick settings") -> return Cmd.QuickSettings
            exact(t, "انزل", "نزل", "تحت", "انزل تحت", "scroll down", "down") -> return Cmd.ScrollDown
            exact(t, "اطلع", "فوق", "اطلع فوق", "scroll up", "up") -> return Cmd.ScrollUp
            exact(t, "صور الشاشة", "سكرين شوت", "لقطة شاشة", "screenshot", "take screenshot") -> return Cmd.Screenshot
            exact(t, "اقفل الشاشة", "قفل الشاشة", "lock", "lock screen") -> return Cmd.Lock
            exact(t, "قائمة الطاقة", "power menu") -> return Cmd.PowerMenu
            exact(t, "ارفع الصوت", "علي الصوت", "volume up") -> return Cmd.VolUp
            exact(t, "وطي الصوت", "اخفض الصوت", "قلل الصوت", "volume down") -> return Cmd.VolDown
            exact(t, "اظهر الارقام", "ورني الارقام", "ارقم", "show numbers") -> return Cmd.ShowNumbers
            exact(t, "اخفي الارقام", "شيل الارقام", "hide numbers") -> return Cmd.HideNumbers
            exact(t, "امسح", "امسح النص", "clear", "clear text") -> return Cmd.ClearText
            exact(t, "وقف", "وقف الاستماع", "stop", "stop listening") -> return Cmd.Stop
        }

        rest(raw, "افتح تطبيق", "افتح", "شغل", "open app", "open", "launch")?.let {
            return Cmd.OpenApp(it.norm)
        }
        rest(raw, "اضغط علي", "اضغط", "دوس علي", "دوس", "اختار", "click on", "click", "tap", "press")?.let {
            val n = Norm.number(it.norm)
            return if (n != null) Cmd.ClickNumber(n) else Cmd.Click(it.norm)
        }
        rest(raw, "اكتب", "type", "write")?.let {
            return Cmd.Type(it.raw)
        }
        rest(raw, "اتصل ب", "اتصل", "كلم", "call")?.let {
            return Cmd.Call(it.norm)
        }
        return null
    }
}
