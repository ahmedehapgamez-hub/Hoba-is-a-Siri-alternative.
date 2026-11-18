package com.hoba.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var langBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        fun label(t: String, size: Float, color: String = "#222222"): TextView = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(Color.parseColor(color))
            setPadding(0, pad / 2, 0, pad / 2)
        }

        fun btn(t: String, onClick: () -> Unit): Button = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        root.addView(label("هوبا بديل سيري", 28f, "#6C47FF"))
        root.addView(label("مساعد صوتي بيتحكم في التليفون بالكلام. خلّص الخطوات دي مرة واحدة:", 16f))

        root.addView(btn("١) إذن الميكروفون") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        root.addView(btn("٢) إذن الاتصال وجهات الاتصال") {
            requestPermissions(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS), 2
            )
        })
        root.addView(btn("٣) تفعيل خدمة هوبا (إمكانية الوصول)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        langBtn = btn("") { toggleLanguage() }
        root.addView(langBtn)
        refreshLanguageLabel()

        root.addView(
            label(
                "بعد التفعيل هتلاقي زرار 🎤 عايم على الشاشة:\n" +
                    "• ضغطة واحدة: قول أمر واحد\n" +
                    "• ضغطة طويلة: استماع مستمر (قول \"وقف\" عشان يقف)",
                15f
            )
        )
        root.addView(
            label(
                "الأوامر:\n" +
                    "• افتح واتساب / افتح الكاميرا\n" +
                    "• ارجع / الرئيسية / التطبيقات الاخيرة / الاشعارات\n" +
                    "• انزل / اطلع\n" +
                    "• اضغط على [اسم الزرار]\n" +
                    "• اظهر الارقام ← اضغط 5 ← اخفي الارقام\n" +
                    "• اكتب [النص] / امسح\n" +
                    "• اتصل بـ [اسم من جهات الاتصال]\n" +
                    "• صور الشاشة / اقفل الشاشة\n" +
                    "• ارفع الصوت / وطي الصوت",
                15f
            )
        )

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun toggleLanguage() {
        val next = if (Prefs.language(this) == "ar-EG") "en-US" else "ar-EG"
        Prefs.setLanguage(this, next)
        refreshLanguageLabel()
    }

    private fun refreshLanguageLabel() {
        langBtn.text = if (Prefs.language(this) == "ar-EG") {
            "لغة الأوامر: عربي (مصر) — اضغط للتغيير"
        } else {
            "Command language: English — tap to change"
        }
    }
}
