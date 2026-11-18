package com.hoba.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class HobaService : AccessibilityService() {

    companion object {
        /** أسماء بتتقال بالعربي → اسم التطبيق زي ما بيظهر على التليفون (إنجليزي). */
        private val ALIASES: Map<String, String> = mapOf(
            "واتساب" to "whatsapp", "واتس اب" to "whatsapp", "واتس" to "whatsapp",
            "فيسبوك" to "facebook", "فيس بوك" to "facebook", "ماسنجر" to "messenger",
            "يوتيوب" to "youtube", "كروم" to "chrome", "انستجرام" to "instagram",
            "انستغرام" to "instagram", "تيك توك" to "tiktok", "تليجرام" to "telegram",
            "تلغرام" to "telegram", "سناب" to "snapchat", "نتفليكس" to "netflix",
            "كاميرا" to "camera", "الكاميرا" to "camera", "اعدادات" to "settings",
            "الاعدادات" to "settings", "تليفون" to "phone", "هاتف" to "phone",
            "رسايل" to "messages", "رسائل" to "messages", "معرض" to "gallery",
            "صور" to "photos", "ساعه" to "clock", "حاسبه" to "calculator",
            "الة حاسبه" to "calculator", "خرائط" to "maps", "جيميل" to "gmail",
            "تقويم" to "calendar", "ملفات" to "files", "مذكرات" to "notes"
        ).mapKeys { Norm.norm(it.key) }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var button: TextView? = null
    private var numbersLayer: FrameLayout? = null
    private var numbered: List<AccessibilityNodeInfo> = emptyList()

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var continuous = false

    // ───────────────────────── دورة حياة الخدمة ─────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (button == null) addButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        continuous = false
        stopRecognizer()
        hideNumbers()
        try {
            button?.let { wm.removeView(it) }
        } catch (e: Exception) {
        }
        button = null
        super.onDestroy()
    }

    // ───────────────────────── زرار الميكروفون العايم ─────────────────────────

    private fun addButton() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply {
            text = "🎤"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#6C47FF"))
            }
            setOnClickListener { toggleListening() }
            setOnLongClickListener {
                continuous = !continuous
                toast(if (continuous) "الاستماع المستمر شغال" else "الاستماع المستمر اتقفل")
                if (continuous && !listening) startListening()
                if (!continuous) stopRecognizer()
                updateButton()
                true
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 240
        }
        wm.addView(tv, lp)
        button = tv
    }

    private fun updateButton() {
        val color = when {
            listening -> "#E53935"
            continuous -> "#2E7D32"
            else -> "#6C47FF"
        }
        (button?.background as? GradientDrawable)?.setColor(Color.parseColor(color))
    }

    // ───────────────────────── التعرف على الصوت ─────────────────────────

    private fun toggleListening() {
        if (listening) {
            continuous = false
            stopRecognizer()
            updateButton()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("افتح تطبيق هوبا وادّي إذن الميكروفون")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("خدمة التعرف على الصوت مش متاحة على التليفون ده")
            return
        }
        stopRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.language(this@HobaService))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        listening = true
        updateButton()
        recognizer?.startListening(intent)
    }

    private fun stopRecognizer() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
        listening = false
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            listening = false
            updateButton()
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!quiet) toast("مشكلة في التعرف على الصوت (كود $error)")
            val fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_CLIENT
            if (continuous && !fatal) {
                handler.postDelayed({ if (continuous && !listening) startListening() }, 600)
            } else if (fatal) {
                continuous = false
                updateButton()
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            updateButton()
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            handleSpeech(heard)
            if (continuous) {
                handler.postDelayed({ if (continuous && !listening) startListening() }, 700)
            }
        }
    }

    private fun handleSpeech(heard: List<String>) {
        if (heard.isEmpty()) return
        for (h in heard) {
            val cmd = Parser.parse(h) ?: continue
            toast("🎤 $h")
            execute(cmd)
            return
        }
        toast("ما فهمتش: ${heard[0]}")
    }

    // ───────────────────────── تنفيذ الأوامر ─────────────────────────

    private fun execute(cmd: Cmd) {
        when (cmd) {
            is Cmd.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            is Cmd.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            is Cmd.Recents -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            is Cmd.Notifications -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            is Cmd.QuickSettings -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            is Cmd.Screenshot -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            is Cmd.Lock -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            is Cmd.PowerMenu -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            is Cmd.ScrollDown -> scroll(true)
            is Cmd.ScrollUp -> scroll(false)
            is Cmd.VolUp -> volume(true)
            is Cmd.VolDown -> volume(false)
            is Cmd.ShowNumbers -> showNumbers()
            is Cmd.HideNumbers -> hideNumbers()
            is Cmd.ClearText -> setFieldText("", append = false)
            is Cmd.Stop -> {
                continuous = false
                stopRecognizer()
                updateButton()
            }
            is Cmd.OpenApp -> openApp(cmd.name)
            is Cmd.Click -> clickByText(cmd.target)
            is Cmd.ClickNumber -> {
                val node = numbered.getOrNull(cmd.n - 1)
                if (node == null) {
                    toast("قول \"اظهر الارقام\" الأول")
                } else {
                    hideNumbers()
                    clickNode(node)
                }
            }
            is Cmd.Type -> setFieldText(cmd.text, append = true)
            is Cmd.Call -> call(cmd.name)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    // ── فتح التطبيقات ──

    private fun openApp(spoken: String) {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)

        val names = LinkedHashSet<String>()
        for (v in listOf(spoken, spoken.removePrefix("ال"))) {
            if (v.isEmpty()) continue
            names.add(v)
            ALIASES[v]?.let { names.add(it) }
        }

        val exact = apps.firstOrNull { ri ->
            val l = Norm.norm(ri.loadLabel(pm).toString())
            names.any { it == l }
        }
        val hit = exact ?: apps.firstOrNull { ri ->
            val l = Norm.norm(ri.loadLabel(pm).toString())
            names.any { l.contains(it) }
        }
        if (hit == null) {
            toast("مش لاقي تطبيق: $spoken")
            return
        }
        val intent = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
        if (intent == null) {
            toast("مقدرتش أفتح: $spoken")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ── قراءة الشاشة والضغط ──

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, clickableOnly: Boolean) {
        if (n == null || !n.isVisibleToUser) return
        val useful = if (clickableOnly) {
            n.isClickable
        } else {
            n.isClickable || n.text != null || n.contentDescription != null
        }
        if (useful) out.add(n)
        for (i in 0 until n.childCount) collect(n.getChild(i), out, clickableOnly)
    }

    private fun label(n: AccessibilityNodeInfo): String =
        Norm.norm(listOfNotNull(n.text, n.contentDescription).joinToString(" "))

    private fun clickByText(target: String) {
        val root = rootInActiveWindow ?: return
        val want = Norm.norm(target)
        if (want.isEmpty()) return
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, clickableOnly = false)
        val match = all.firstOrNull { label(it) == want }
            ?: all.firstOrNull { label(it).isNotEmpty() && label(it).contains(want) }
        if (match == null) {
            toast("مش لاقي على الشاشة: $target")
            return
        }
        clickNode(match)
    }

    private fun clickNode(n: AccessibilityNodeInfo) {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            cur = cur.parent
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ── Scroll ──

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null || !n.isVisibleToUser) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            findScrollable(n.getChild(i))?.let { return it }
        }
        return null
    }

    private fun scroll(forward: Boolean) {
        val node = findScrollable(rootInActiveWindow)
        if (node == null) {
            toast("مفيش حاجة تتعمل لها scroll")
            return
        }
        node.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    // ── ترقيم العناصر ──

    private fun showNumbers() {
        hideNumbers()
        val root = rootInActiveWindow ?: return
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, clickableOnly = true)
        val items = all.take(99)
        numbered = items

        val layer = FrameLayout(this)
        items.forEachIndexed { i, n ->
            val r = Rect()
            n.getBoundsInScreen(r)
            val badge = TextView(this).apply {
                text = "${i + 1}"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC6C47FF"))
                setPadding(8, 2, 8, 2)
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = r.left
            lp.topMargin = r.top
            layer.addView(badge, lp)
        }
        val wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(layer, wlp)
        numbersLayer = layer
    }

    private fun hideNumbers() {
        try {
            numbersLayer?.let { wm.removeView(it) }
        } catch (e: Exception) {
        }
        numbersLayer = null
        numbered = emptyList()
    }

    // ── الكتابة ──

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            findEditable(n.getChild(i))?.let { return it }
        }
        return null
    }

    private fun setFieldText(text: String, append: Boolean) {
        val root = rootInActiveWindow ?: return
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
        if (field == null) {
            toast("مفيش خانة كتابة على الشاشة")
            return
        }
        val old = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
        val newText = if (append && old.isNotEmpty()) "$old $text" else text
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ── الصوت ──

    private fun volume(up: Boolean) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    // ── الاتصال ──

    private fun lookupNumber(name: String): String? {
        val q = Norm.norm(name)
        if (q.isEmpty()) return null
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        ) ?: return null
        cursor.use {
            while (it.moveToNext()) {
                val display = Norm.norm(it.getString(1) ?: "")
                if (display.contains(q)) return it.getString(0)
            }
        }
        return null
    }

    private fun call(spoken: String) {
        val digits = Norm.latinDigits(spoken).filter { it.isDigit() }
        var number: String? = if (digits.length >= 5) digits else null

        if (number == null && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            // لو الاسم لزق فيه حرف الباء ("اتصل باحمد") جرّب بدونها كمان
            val variants = listOfNotNull(spoken, if (spoken.startsWith("ب") && spoken.length > 2) spoken.drop(1) else null)
            for (v in variants) {
                number = lookupNumber(v)
                if (number != null) break
            }
        }
        if (number == null) {
            toast("مش لاقي جهة اتصال: $spoken")
            return
        }
        val canCall = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(
            if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:" + Uri.encode(number))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
