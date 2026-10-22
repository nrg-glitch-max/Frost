package com.frost.tvlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppItem(val pkg: String, val label: String, val intent: Intent, val icon: Drawable)

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var bgView: BackgroundView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var grid: GridLayout
    private lateinit var hint: TextView
    private lateinit var wifiBtn: TextView
    private lateinit var themesBtn: TextView
    private lateinit var overlay: FrameLayout
    private lateinit var themeGrid: GridLayout
    private lateinit var tabViews: List<TextView>

    private var apps = mutableListOf<AppItem>()
    private var moving = -1          // index of the app being moved, -1 when not in move mode
    private var cols = 5
    private var tileW = 0
    private var tileH = 0
    private var themeCols = 5
    private var themeTab = 0         // 0 = wallpapers, 1 = palettes

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                updateWifi()
            } else if (moving < 0) {
                reloadApps(false)
            }
        }
    }

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("frost", MODE_PRIVATE)

        val screenW = resources.displayMetrics.widthPixels
        val pad = dp(56)
        val gap = dp(8)
        cols = ((screenW - 2 * pad) / dp(150)).coerceIn(4, 8)
        tileW = (screenW - 2 * pad) / cols - 2 * gap
        tileH = (tileW * 0.92f).toInt()
        themeCols = ((screenW - dp(120)) / dp(156)).coerceIn(2, 5)

        val root = FrameLayout(this)

        bgView = BackgroundView(this)
        bgView.bg = Backgrounds.byId(prefs.getString("bg", null))
        root.addView(bgView, FrameLayout.LayoutParams(MATCH, MATCH))

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(pad, dp(30), pad, dp(12))
        content.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(buildAppsHeader(), LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(22)
        })

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        scroll.clipChildren = false
        scroll.setPadding(0, dp(10), 0, dp(24))
        grid = GridLayout(this)
        grid.columnCount = cols
        grid.clipChildren = false
        grid.clipToPadding = false
        scroll.addView(grid, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        hint = TextView(this)
        hint.text = "Move mode   •   Arrow keys to move   •   OK to place"
        hint.setTextColor(Color.WHITE)
        hint.textSize = 15f
        hint.setPadding(dp(24), dp(12), dp(24), dp(12))
        hint.background = Glass.panel(this, 26, 0xB0101420.toInt(), Glass.ACCENT, 2)
        hint.visibility = View.GONE
        root.addView(
            hint,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(28)
            }
        )

        overlay = buildOverlay()
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)

        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        updateWifi()
        reloadApps(true)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- top bar

    private fun buildTopBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val clockCol = LinearLayout(this)
        clockCol.orientation = LinearLayout.VERTICAL

        val clock = TextClock(this)
        clock.setFormat12Hour("h:mm a")
        clock.setFormat24Hour("HH:mm")
        clock.setTextColor(Color.WHITE)
        clock.textSize = 42f
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clockCol.addView(clock)

        val date = TextClock(this)
        date.setFormat12Hour("EEEE, d MMMM")
        date.setFormat24Hour("EEEE, d MMMM")
        date.setTextColor(0xCCFFFFFF.toInt())
        date.textSize = 16f
        clockCol.addView(date)

        bar.addView(clockCol, LinearLayout.LayoutParams(WRAP, WRAP))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        wifiBtn = glassButton("Wi-Fi") { openSystemSettings(Settings.ACTION_WIFI_SETTINGS) }
        val settingsBtn = glassButton("Settings") { openSystemSettings(Settings.ACTION_SETTINGS) }
        themesBtn = glassButton("Themes") { openThemes() }

        for (b in listOf(wifiBtn, settingsBtn, themesBtn)) {
            bar.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) })
        }
        return bar
    }

    private fun buildAppsHeader(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = "Apps"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.typeface = Typeface.DEFAULT_BOLD
        row.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))

        val tip = TextView(this)
        tip.text = "Hold OK (or press Menu) on an app to rearrange"
        tip.setTextColor(0x99FFFFFF.toInt())
        tip.textSize = 14f
        row.addView(tip, LinearLayout.LayoutParams(WRAP, WRAP))
        return row
    }

    private fun glassButton(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = 16f
        b.gravity = Gravity.CENTER
        b.setPadding(dp(22), dp(11), dp(22), dp(11))
        b.isFocusable = true
        b.isClickable = true
        b.background = Glass.focusable(this, 24)
        b.focusScale()
        b.setOnClickListener { onClick() }
        return b
    }

    // ---------------------------------------------------------------- Wi-Fi / settings

    private fun updateWifi() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = try {
            cm.activeNetworkInfo
        } catch (e: Exception) {
            null
        }
        wifiBtn.text = when {
            info == null || !info.isConnected -> "Wi-Fi: Offline"
            info.type == ConnectivityManager.TYPE_ETHERNET -> "Ethernet: Connected"
            info.type == ConnectivityManager.TYPE_WIFI -> "Wi-Fi: Connected"
            else -> "Network: Connected"
        }
    }

    private fun openSystemSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Toast.makeText(this, "Couldn't open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- apps

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val found = LinkedHashMap<String, AppItem>()
        // TV entry points first so TV-specific activities win over phone ones.
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || found.containsKey(pkg)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(cat)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                found[pkg] = AppItem(pkg, ri.loadLabel(pm).toString(), launch, ri.loadIcon(pm))
            }
        }
        val saved = (prefs.getString("order", "") ?: "").split("\n").filter { it.isNotEmpty() }
        val position = HashMap<String, Int>()
        saved.forEachIndexed { i, p -> position[p] = i }
        return found.values.sortedWith(
            compareBy<AppItem>({ position[it.pkg] ?: Int.MAX_VALUE }, { it.label.lowercase() })
        )
    }

    private fun reloadApps(focusFirst: Boolean) {
        Thread {
            val list = loadApps()
            runOnUiThread {
                if (isDestroyed || moving >= 0) return@runOnUiThread
                if (!focusFirst && list.map { it.pkg } == apps.map { it.pkg }) return@runOnUiThread
                val focusIndex = if (focusFirst) {
                    0
                } else {
                    grid.focusedChild?.let { grid.indexOfChild(it) } ?: -1
                }
                apps = list.toMutableList()
                render(focusIndex)
            }
        }.start()
    }

    private fun render(focusIndex: Int) {
        grid.removeAllViews()
        apps.forEachIndexed { i, app -> grid.addView(makeTile(i, app), tileParams()) }
        if (focusIndex in apps.indices) grid.getChildAt(focusIndex)?.requestFocus()
    }

    private fun tileParams(): GridLayout.LayoutParams {
        val lp = GridLayout.LayoutParams()
        lp.width = tileW
        lp.height = tileH
        lp.setMargins(dp(8), dp(8), dp(8), dp(8))
        return lp
    }

    private fun makeTile(index: Int, app: AppItem): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(dp(8), dp(10), dp(8), dp(10))
        tile.background = Glass.focusable(this, 22)
        tile.isFocusable = true
        tile.isClickable = true
        tile.isLongClickable = true
        tile.isSelected = (index == moving)
        tile.focusScale()

        val iconPx = (tileW * 0.5f).toInt()
        val icon = ImageView(this)
        icon.setImageDrawable(app.icon)
        tile.addView(icon, LinearLayout.LayoutParams(iconPx, iconPx))

        val label = TextView(this)
        label.text = app.label
        label.setTextColor(Color.WHITE)
        label.textSize = 14f
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        tile.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })

        tile.setOnClickListener {
            if (moving >= 0) endMove() else launch(app)
        }
        tile.setOnLongClickListener {
            if (moving < 0) startMove(index)
            true
        }
        tile.setOnKeyListener { _, keyCode, event -> onTileKey(index, keyCode, event) }
        return tile
    }

    private fun launch(app: AppItem) {
        try {
            startActivity(app.intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- rearranging

    private fun startMove(index: Int) {
        moving = index
        hint.visibility = View.VISIBLE
        grid.getChildAt(index)?.isSelected = true
    }

    private fun endMove() {
        if (moving < 0) return
        val index = moving
        moving = -1
        hint.visibility = View.GONE
        grid.getChildAt(index)?.isSelected = false
        prefs.edit().putString("order", apps.joinToString("\n") { it.pkg }).apply()
    }

    private fun onTileKey(index: Int, keyCode: Int, e: KeyEvent): Boolean {
        if (moving != index) {
            // Menu button is a shortcut for "start moving this app".
            if (moving < 0 && keyCode == KeyEvent.KEYCODE_MENU) {
                if (e.action == KeyEvent.ACTION_UP) startMove(index)
                return true
            }
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (e.action == KeyEvent.ACTION_DOWN) {
                    val target = when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> index - 1
                        KeyEvent.KEYCODE_DPAD_RIGHT -> index + 1
                        KeyEvent.KEYCODE_DPAD_UP -> index - cols
                        else -> index + cols
                    }
                    if (target in apps.indices) {
                        val item = apps.removeAt(index)
                        apps.add(target, item)
                        moving = target
                        render(target)
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK -> {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) endMove()
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- themes (wallpapers + palettes)

    private fun buildOverlay(): FrameLayout {
        val ov = FrameLayout(this)
        ov.setBackgroundColor(0xB0000000.toInt())
        ov.isClickable = true
        ov.visibility = View.GONE

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(28), dp(24), dp(28), dp(22))
        panel.background = Glass.panel(this, 28, 0x40FFFFFF, 0x66FFFFFF, 1, 0x1FFFFFFF)

        val title = TextView(this)
        title.text = "Background"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        panel.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabViews = listOf("Wallpapers", "Palettes").mapIndexed { i, name ->
            glassButton(name) {
                themeTab = i
                buildThemeGrid()
                tabViews[i].requestFocus()
            }
        }
        for (t in tabViews) {
            tabs.addView(t, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) })
        }
        panel.addView(tabs, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = dp(14)
            bottomMargin = dp(6)
        })

        themeGrid = GridLayout(this)
        themeGrid.columnCount = themeCols
        themeGrid.clipChildren = false
        themeGrid.clipToPadding = false

        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.clipToPadding = false
        sc.clipChildren = false
        sc.setPadding(dp(4), dp(8), dp(4), dp(8))
        sc.addView(themeGrid, FrameLayout.LayoutParams(WRAP, WRAP))
        panel.addView(sc, LinearLayout.LayoutParams(WRAP, dp(300)))

        ov.addView(panel, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        return ov
    }

    private fun buildThemeGrid() {
        val list = if (themeTab == 0) Backgrounds.wallpapers else Backgrounds.palettes
        val currentId = bgView.bg.id
        themeGrid.removeAllViews()
        tabViews.forEachIndexed { i, t -> t.isSelected = (i == themeTab) }

        list.forEachIndexed { i, bg ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.setPadding(dp(8), dp(8), dp(8), dp(6))
            cell.background = Glass.focusable(this, 16)
            cell.isFocusable = true
            cell.isClickable = true
            cell.focusScale(1.05f)

            cell.addView(PreviewView(this, bg), LinearLayout.LayoutParams(dp(128), dp(72)))

            val name = TextView(this)
            name.text = if (bg.id == currentId) "✓ ${bg.name}" else bg.name
            name.setTextColor(if (bg.id == currentId) Glass.ACCENT else Color.WHITE)
            name.textSize = 14f
            name.gravity = Gravity.CENTER
            cell.addView(name, LinearLayout.LayoutParams(dp(128), WRAP).apply { topMargin = dp(6) })

            cell.setOnClickListener {
                bgView.bg = bg
                prefs.edit().putString("bg", bg.id).apply()
                buildThemeGrid()
                themeGrid.getChildAt(i)?.requestFocus()
            }

            val lp = GridLayout.LayoutParams()
            lp.setMargins(dp(6), dp(6), dp(6), dp(6))
            themeGrid.addView(cell, lp)
        }
    }

    private fun openThemes() {
        themeTab = if (bgView.bg.id.startsWith("pal_")) 1 else 0
        buildThemeGrid()
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overlay.visibility = View.VISIBLE
        tabViews[themeTab].requestFocus()
    }

    private fun closeThemes() {
        overlay.visibility = View.GONE
        content.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        themesBtn.requestFocus()
    }

    // ---------------------------------------------------------------- navigation

    override fun onBackPressed() {
        // A launcher never exits on Back; Back just closes whatever is open.
        when {
            overlay.visibility == View.VISIBLE -> closeThemes()
            moving >= 0 -> endMove()
            else -> {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: reset to a clean state.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (overlay.visibility == View.VISIBLE) closeThemes()
            endMove()
            scroll.smoothScrollTo(0, 0)
            grid.getChildAt(0)?.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateWifi()
    }
}
