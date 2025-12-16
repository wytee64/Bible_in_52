package com.wytee

import android.R
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.wytee.ui.theme.BibleIn52Theme
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

data class Week(
    val week: Int,
    val title: String,
    val days: List<String>
)

data class CalendarDay(
    val date: LocalDate,
    val hasReading: Boolean,
    val isCompleted: Boolean,
    val isToday: Boolean
)

data class Settings(val reminderEnabled: Boolean, val reminderTime: String)


private const val PREFS_NAME = "BibleIn52Prefs"

fun loadCompletedDays(context: Context): MutableMap<String, Boolean> {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val loadedMap = mutableMapOf<String, Boolean>()

    sharedPrefs.all.forEach { (key, value) ->
        if (key.matches(Regex("w\\d+d\\d+")) && value is Boolean && value) {
            loadedMap[key] = true
        }
    }
    return loadedMap
}

fun saveCompletedDays(context: Context, completedDays: Map<String, Boolean>) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().apply {
        sharedPrefs.all.keys.filter { it.matches(Regex("w\\d+d\\d+")) }.forEach { key ->
            remove(key)
        }

        completedDays.filterValues { it }.forEach { (key, _) ->
            putBoolean(key, true)
        }
        apply()
    }
}

fun loadSettings(context: Context): Settings {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = sharedPrefs.getBoolean("reminderEnabled", true)
    val time = sharedPrefs.getString("reminderTime", "09:00") ?: "09:00"
    return Settings(enabled, time)
}

fun saveSettings(context: Context, reminderEnabled: Boolean, reminderTime: String) {
    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sharedPrefs.edit().apply {
        putBoolean("reminderEnabled", reminderEnabled)
        putString("reminderTime", reminderTime)
        apply()
    }
}



class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        setContent {
            BibleIn52Theme {
                BibleReadingApp()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bible_reading",
                "Bible Reading Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to read the Bible"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "bible_reading")
            .setSmallIcon(R.drawable.ic_menu_today)
            .setContentTitle("Time to read!")
            .setContentText("Your daily Bible reading is waiting")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}

@SuppressLint("MutableCollectionMutableState")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BibleReadingApp() {
    val context = LocalContext.current
    val initialCompletedDays = remember {
        loadCompletedDays(context)
    }
    var completedDays by remember {
        mutableStateOf(initialCompletedDays)
    }
    val initialSettings = remember {
        loadSettings(context)
    }
    var reminderTime by remember {
        mutableStateOf(initialSettings.reminderTime)
    }
    var reminderEnabled by remember {
        mutableStateOf(initialSettings.reminderEnabled)
    }
    var selectedTab by remember {
        mutableIntStateOf(0)
    }
    val weeks = remember {
        getWeeksData()
    }
    val streak = calculateStreak(completedDays)
    val stats = calculateStats(completedDays, weeks)
    val totalDays = weeks.size * 7
    val completed = completedDays.values.count { it }
    val progress = (completed.toFloat() / totalDays.toFloat())

    LaunchedEffect(completedDays) {
        saveCompletedDays(context, completedDays)
    }

    LaunchedEffect(reminderEnabled, reminderTime) {
        saveSettings(context, reminderEnabled, reminderTime)

        if (reminderEnabled) {
            scheduleReminder(context, reminderTime)
        } else {
            cancelReminder(context)
        }
    }


    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFFAFAFA),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Reading") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF5E6AD2),
                        selectedTextColor = Color(0xFF5E6AD2),
                        indicatorColor = Color(0xFFEEF0FF)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Calendar") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF5E6AD2),
                        selectedTextColor = Color(0xFF5E6AD2),
                        indicatorColor = Color(0xFFEEF0FF)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF5E6AD2),
                        selectedTextColor = Color(0xFF5E6AD2),
                        indicatorColor = Color(0xFFEEF0FF)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F7))
        ) {
            when (selectedTab) {
                0 -> ReadingPlanTab(weeks, completedDays, progress, streak, stats) { weekNum, dayIndex ->
                    val key = "w${weekNum}d${dayIndex}"
                    completedDays = completedDays.toMutableMap().apply {
                        this[key] = !(this[key] ?: false) // Toggling state
                    }
                }
                1 -> CalendarTab(completedDays, weeks)
                2 -> SettingsTab(
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    onReminderToggle = { reminderEnabled = it },
                    onTimeChange = { reminderTime = it }
                )
            }
        }
    }
}


data class ReadingStats(
    val booksCompleted: Int,
    val totalChapters: Int,
    val booksInProgress: Int
)

fun calculateStats(completedDays: Map<String, Boolean>, weeks: List<Week>): ReadingStats {
    val bookChapterMap = mapOf(
        "Genesis" to 50, "Exodus" to 40, "Leviticus" to 27, "Numbers" to 36, "Deuteronomy" to 34,
        "Joshua" to 24, "Judges" to 21, "Ruth" to 4, "1 Samuel" to 31, "2 Samuel" to 24,
        "1 Kings" to 22, "2 Kings" to 25, "1 Chronicles" to 29, "2 Chronicles" to 36,
        "Ezra" to 10, "Nehemiah" to 13, "Esther" to 10, "Job" to 42,
        "Psalm" to 150, "Proverbs" to 31, "Ecclesiastes" to 12, "Song of Solomon" to 8,
        "Isaiah" to 66, "Jeremiah" to 52, "Lamentations" to 5, "Ezekiel" to 48, "Daniel" to 12,
        "Hosea" to 14, "Joel" to 3, "Amos" to 9, "Obadiah" to 1, "Jonah" to 4,
        "Micah" to 7, "Nahum" to 3, "Habakkuk" to 3, "Zephaniah" to 3, "Haggai" to 2,
        "Zechariah" to 14, "Malachi" to 4,
        "Matthew" to 28, "Mark" to 16, "Luke" to 24, "John" to 21, "Acts" to 28,
        "Romans" to 16, "1 Corinthians" to 16, "2 Corinthians" to 13, "Galatians" to 6,
        "Ephesians" to 6, "Philippians" to 4, "Colossians" to 4, "1 Thessalonians" to 5,
        "2 Thessalonians" to 3, "1 Timothy" to 6, "2 Timothy" to 4, "Titus" to 3,
        "Philemon" to 1, "Hebrews" to 13, "James" to 5, "1 Peter" to 5, "2 Peter" to 3,
        "1 John" to 5, "2 John" to 1, "3 John" to 1, "Jude" to 1, "Revelation" to 22
    )

    val completedReadings = weeks.flatMap { week ->
        week.days.mapIndexed { index, day ->
            if (completedDays["w${week.week}d$index"] == true) day else null
        }.filterNotNull()
    }

    var totalChapters = 0
    val bookProgress = mutableMapOf<String, MutableSet<Int>>()

    completedReadings.forEach { reading ->
        val parts = reading.split("(")[0].trim().split(" ")
        if (parts.size >= 2) {
            val bookName = parts.dropLast(1).joinToString(" ")
            val chapterRange = parts.last()

            val chapters = when {
                chapterRange.contains("-") -> {
                    val (start, end) = chapterRange.split("-").map { it.toIntOrNull() ?: 0 }
                    (start..end).toList()
                }
                chapterRange.contains(",") -> {
                    chapterRange.split(",").mapNotNull { it.trim().toIntOrNull() }
                }
                else -> listOf(chapterRange.toIntOrNull() ?: 0)
            }

            chapters.forEach { chapter ->
                if (chapter > 0) {
                    totalChapters++
                    bookProgress.getOrPut(bookName) { mutableSetOf() }.add(chapter)
                }
            }
        }
    }

    val booksCompleted = bookProgress.count { (book, chapters) ->
        val totalChaptersInBook = bookChapterMap[book] ?: 0
        chapters.size >= totalChaptersInBook
    }

    val booksInProgress = bookProgress.count { (book, chapters) ->
        val totalChaptersInBook = bookChapterMap[book] ?: 0
        chapters.isNotEmpty() && chapters.size < totalChaptersInBook
    }

    return ReadingStats(booksCompleted, totalChapters, booksInProgress)
}

@Composable
fun ReadingPlanTab(
    weeks: List<Week>,
    completedDays: Map<String, Boolean>,
    progress: Float,
    streak: Int,
    stats: ReadingStats,
    onDayToggle: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bible In 52",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = streak.toString(),
                        label = "Day Streak",
                        icon = ""
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${(progress * 100).toInt()}%",
                        label = "Complete",
                        icon = ""
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = stats.booksCompleted.toString(),
                        label = "Books Done",
                        icon = ""
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = stats.totalChapters.toString(),
                        label = "Chapters",
                        icon = ""
                    )
                }
            }
        }

        item {
            Text(
                text = "Reading Plan",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        items(weeks) { week ->
            WeekCard(
                week = week,
                completedDays = completedDays,
                onDayToggle = onDayToggle
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1E)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarTab(completedDays: Map<String, Boolean>, weeks: List<Week>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Calendar",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            CalendarView(completedDays, weeks)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarView(completedDays: Map<String, Boolean>, weeks: List<Week>) {
    val today = LocalDate.now()
    val currentMonth = today.month
    val currentYear = today.year

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "${currentMonth.getDisplayName(TextStyle.FULL, Locale.getDefault())} $currentYear",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val firstDayOfMonth = LocalDate.of(currentYear, currentMonth, 1)
            val daysInMonth = currentMonth.length(today.isLeapYear)
            val startDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7

            val calendarDays = mutableListOf<CalendarDay?>()
            repeat(startDayOfWeek) { calendarDays.add(null) }

            for (day in 1..daysInMonth) {
                val date = LocalDate.of(currentYear, currentMonth, day)
                val dayKey = getDayKeyForDate(date, weeks)
                calendarDays.add(CalendarDay(
                    date = date,
                    hasReading = dayKey != null,
                    isCompleted = dayKey?.let { completedDays[it] } ?: false,
                    isToday = date == today
                ))
            }

            val rows = calendarDays.chunked(7)
            rows.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                CalendarDayCell(day)
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarDayCell(day: CalendarDay) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    day.isCompleted -> Color(0xFF34C759)
                    day.hasReading -> Color(0xFFEEF0FF)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (day.isToday) 2.dp else 0.dp,
                color = Color(0xFF5E6AD2),
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            fontSize = 14.sp,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                day.isCompleted -> Color.White
                else -> Color(0xFF1C1C1E)
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun getDayKeyForDate(date: LocalDate, weeks: List<Week>): String? {
    val startDate = LocalDate.of(2025, 1, 1)
    val daysSinceStart = ChronoUnit.DAYS.between(startDate, date).toInt()

    if (daysSinceStart < 0 || daysSinceStart >= weeks.size * 7) return null

    val weekNum = (daysSinceStart / 7) + 1
    val dayNum = daysSinceStart % 7

    return "w${weekNum}d${dayNum}"
}

@Composable
fun SettingsTab(
    reminderEnabled: Boolean,
    reminderTime: String,
    onReminderToggle: (Boolean) -> Unit,
    onTimeChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Daily Reminders",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1C1E)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable reminders",
                            fontSize = 15.sp,
                            color = Color(0xFF1C1C1E)
                        )
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = onReminderToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF5E6AD2)
                            )
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Reminder time",
                            fontSize = 13.sp,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeButton("9:00 AM", "09:00", reminderTime, onTimeChange)
                            TimeButton("12:00 PM", "12:00", reminderTime, onTimeChange)
                            TimeButton("8:00 PM", "20:00", reminderTime, onTimeChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeButton(
    label: String,
    value: String,
    currentTime: String,
    onTimeChange: (String) -> Unit
) {
    Button(
        onClick = { onTimeChange(value) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (currentTime == value) Color(0xFF5E6AD2) else Color(0xFFF5F5F7),
            contentColor = if (currentTime == value) Color.White else Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun WeekCard(
    week: Week,
    completedDays: Map<String, Boolean>,
    onDayToggle: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Week ${week.week}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF5E6AD2)
            )
            Text(
                text = week.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            week.days.forEachIndexed { index, day ->
                DayItem(
                    dayNumber = index + 1,
                    reading = day,
                    isCompleted = completedDays["w${week.week}d${index}"] ?: false,
                    onClick = { onDayToggle(week.week, index) }
                )
                if (index < week.days.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DayItem(
    dayNumber: Int,
    reading: String,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCompleted) Color(0xFFF0FDF4) else Color(0xFFFAFAFA))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isCompleted) Color(0xFF34C759) else Color.White)
                .border(
                    width = 2.dp,
                    color = if (isCompleted) Color(0xFF34C759) else Color(0xFFE5E5EA),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Day $dayNumber",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8E8E93)
            )
            Text(
                text = reading,
                fontSize = 15.sp,
                color = Color(0xFF1C1C1E),
                lineHeight = 20.sp
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun calculateStreak(completedDays: Map<String, Boolean>): Int {
    val today = LocalDate.now()
    var streak = 0
    var currentDate = today

    for (i in 0..365) {
        val dayKey = getDayKeyForDate(currentDate, getWeeksData())
        if (dayKey != null && completedDays[dayKey] == true) {
            streak++
            currentDate = currentDate.minusDays(1)
        } else {
            break
        }
    }

    return streak
}

fun scheduleReminder(context: Context, time: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        val parts = time.split(":")
        set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        set(Calendar.MINUTE, parts[1].toInt())
        set(Calendar.SECOND, 0)

        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    alarmManager.setRepeating(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        pendingIntent
    )
}

fun cancelReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

fun getWeeksData(): List<Week> {
    return listOf(
        Week(1, "Creation & New Creation", listOf(
            "Genesis 1-3 (The Beginning)",
            "John 1-3 (Jesus: The New Beginning)",
            "Genesis 4-7 (Sin & the Flood)",
            "Romans 5-6 (Sin & Salvation)",
            "Genesis 8-11 (After the Flood)",
            "Revelation 21-22 (New Heaven & Earth)",
            "Psalm 1-8 (Songs of Creation)"
        )),
        Week(2, "Abraham's Journey & Faith", listOf(
            "Genesis 12-15 (God's Call to Abraham)",
            "Hebrews 11 (Heroes of Faith)",
            "Genesis 16-18 (Promises & Waiting)",
            "Romans 4 (Abraham's Faith)",
            "Genesis 19-21 (Sodom & Isaac's Birth)",
            "Galatians 3-4 (Sons of Abraham)",
            "Psalm 9-15 (Trust in God)"
        )),
        Week(3, "Testing & Sacrifice", listOf(
            "Genesis 22-24 (Abraham's Test)",
            "James 1-2 (Testing & Faith)",
            "Genesis 25-27 (Jacob & Esau)",
            "Romans 9 (God's Choice)",
            "Genesis 28-30 (Jacob's Journey)",
            "John 4-5 (Living Water)",
            "Psalm 16-20 (God Our Refuge)"
        )),
        Week(4, "Joseph's Story & God's Plan", listOf(
            "Genesis 37-39 (Joseph Sold & Imprisoned)",
            "1 Peter 1-2 (Suffering & Glory)",
            "Genesis 40-42 (Dreams & Famine)",
            "Acts 7 (Stephen's Speech on Joseph)",
            "Genesis 43-45 (Reunion & Forgiveness)",
            "Matthew 5-6 (Sermon on the Mount Part 1)",
            "Genesis 46-50 (Jacob in Egypt & Joseph's Death)"
        )),
        Week(5, "Moses & Deliverance", listOf(
            "Exodus 1-3 (Moses' Birth & Calling)",
            "Acts 7 (Moses' Life)",
            "Exodus 4-6 (Return to Egypt)",
            "Hebrews 3 (Moses & Jesus)",
            "Exodus 7-10 (The Plagues)",
            "Matthew 7-8 (Sermon on the Mount Part 2 & Miracles)",
            "Psalm 21-27 (Deliverance Songs)"
        )),
        Week(6, "Passover & Crossing", listOf(
            "Exodus 11-13 (Passover & Exodus)",
            "1 Corinthians 5, 10-11 (Christ Our Passover)",
            "Exodus 14-16 (Red Sea & Manna)",
            "John 6 (Bread of Life)",
            "Exodus 17-20 (Water & Ten Commandments)",
            "Matthew 9-10 (Jesus' Authority & Sending)",
            "Psalm 28-33 (Songs of Praise)"
        )),
        Week(7, "Law & Grace", listOf(
            "Exodus 21-24 (The Law)",
            "Romans 7-8 (Law vs. Spirit)",
            "Exodus 25-28 (Tabernacle Plans)",
            "Hebrews 8-9 (True Tabernacle)",
            "Exodus 29-32 (Golden Calf)",
            "Matthew 11-13 (Parables of the Kingdom)",
            "Exodus 33-36 (God's Presence)"
        )),
        Week(8, "Worship & Sacrifice", listOf(
            "Exodus 37-40 (Tabernacle Completed)",
            "Hebrews 10 (One Sacrifice)",
            "Leviticus 1-4 (Offerings)",
            "Hebrews 13 (Christian Worship)",
            "Leviticus 8-10 (Priesthood Begins)",
            "1 Peter 3-5 (Royal Priesthood)",
            "Psalm 34-37 (Worship & Wisdom)"
        )),
        Week(9, "Holiness & Clean Living", listOf(
            "Leviticus 16-19 (Day of Atonement & Holiness)",
            "Romans 12-13 (Living Sacrifice)",
            "Leviticus 23-25 (Feasts & Jubilee)",
            "Colossians 1-4 (Life in Christ)",
            "Leviticus 26-27 (Blessings & Curses)",
            "Matthew 14-16 (Feeding Thousands & Peter's Confession)",
            "Psalm 38-42 (Longing for God)"
        )),
        Week(10, "Wilderness Wandering", listOf(
            "Numbers 1-4 (Census & Camp)",
            "1 Corinthians 12-14 (Spiritual Gifts)",
            "Numbers 10-13 (Journey & Spies)",
            "Hebrews 3-4 (Rest & Rebellion)",
            "Numbers 14-16 (Rebellion & Judgment)",
            "Jude (Warning Against False Teachers)",
            "Numbers 20-22 (Water & Balaam)"
        )),
        Week(11, "Desert Lessons", listOf(
            "Numbers 23-25 (Balaam's Oracles)",
            "2 Peter 1-3 (Growth & False Teachers)",
            "Numbers 27-30 (Leaders & Vows)",
            "Matthew 17-19 (Transfiguration & Teachings)",
            "Numbers 32-34 (Land Division)",
            "Ephesians 1-3 (Mystery Revealed)",
            "Psalm 43-49 (Songs in the Desert)"
        )),
        Week(12, "Moses' Final Words", listOf(
            "Deuteronomy 1-4 (Remember God's Deeds)",
            "Ephesians 4-6 (Walk Worthy)",
            "Deuteronomy 5-8 (Remember the Law)",
            "Matthew 20-22 (Kingdom Teachings)",
            "Deuteronomy 9-11 (Love & Obey)",
            "1 John 1-3 (Walk in Love)",
            "Deuteronomy 28-30 (Blessings & Curses)"
        )),
        Week(13, "Transition to the Promised Land", listOf(
            "Deuteronomy 31-34 (Moses' Death)",
            "Hebrews 12 (Greater Mountain)",
            "Joshua 1-4 (Crossing the Jordan)",
            "Matthew 23-25 (Warnings & End Times)",
            "Joshua 5-8 (Jericho Falls)",
            "Hebrews 1-2 (Jesus Greater Than Angels)",
            "Psalm 50-55 (God Speaks)"
        )),
        Week(14, "Conquest & Victory", listOf(
            "Joshua 9-12 (More Victories)",
            "Philippians 1-4 (Joy in Christ)",
            "Joshua 13-17 (Land Divided)",
            "Matthew 26-28 (Passion & Resurrection)",
            "Joshua 23-24 (Joshua's Farewell)",
            "1 Thessalonians 1-5 (Living in Hope)",
            "Psalm 56-61 (Trust Under Pressure)"
        )),
        Week(15, "The Judges Era Begins", listOf(
            "Judges 1-4 (After Joshua)",
            "2 Thessalonians 1-3 (Stand Firm)",
            "Judges 6-8 (Gideon)",
            "1 Timothy 1-3 (Church Leadership)",
            "Judges 13-16 (Samson)",
            "1 Timothy 4-6 (Godliness & Contentment)",
            "Ruth 1-4 (Redemption Story)"
        )),
        Week(16, "Samuel & the Kingdom Begins", listOf(
            "1 Samuel 1-3 (Samuel's Birth & Call)",
            "2 Timothy 1-4 (Finish the Race)",
            "1 Samuel 8-10 (Israel Demands a King)",
            "Mark 1-3 (Jesus Begins Ministry)",
            "1 Samuel 13-15 (Saul's Failures)",
            "Titus, Philemon (Letters to Leaders)",
            "1 Samuel 16-17 (David & Goliath)"
        )),
        Week(17, "David Rises, Saul Falls", listOf(
            "1 Samuel 18-20 (David & Jonathan)",
            "Mark 4-6 (Parables & Miracles)",
            "1 Samuel 24-26 (David Spares Saul)",
            "Romans 1-3 (All Have Sinned)",
            "1 Samuel 28-31 (Saul's Death)",
            "2 Samuel 1-2 (David Becomes King)",
            "Psalm 62-68 (David's Songs)"
        )),
        Week(18, "David's Kingdom", listOf(
            "2 Samuel 5-8 (David Conquers)",
            "Mark 7-9 (Jesus Revealed)",
            "2 Samuel 11-12 (David & Bathsheba)",
            "Romans 10-11 (Salvation for All)",
            "2 Samuel 15-18 (Absalom's Rebellion)",
            "Mark 10-12 (Servant Leadership)",
            "2 Samuel 22-24 (David's Song & Census)"
        )),
        Week(19, "Solomon's Wisdom", listOf(
            "1 Kings 1-3 (Solomon Becomes King)",
            "James 3-5 (Wisdom from Above)",
            "1 Kings 5-7 (Building the Temple)",
            "Mark 13-14 (End Times & Betrayal)",
            "1 Kings 8-10 (Temple Dedication & Queen of Sheba)",
            "Mark 15-16 (Crucifixion & Resurrection)",
            "Proverbs 1-5 (Beginning of Wisdom)"
        )),
        Week(20, "Wisdom Literature", listOf(
            "Proverbs 8-12 (Wisdom Calls Out)",
            "Luke 1-3 (Jesus' Birth & Beginning)",
            "Proverbs 16-20 (Practical Wisdom)",
            "1 Corinthians 1-4 (God's Wisdom)",
            "Proverbs 25-29 (More Proverbs)",
            "Luke 4-6 (Ministry & Sermon)",
            "Proverbs 30-31 (Excellent Wife)"
        )),
        Week(21, "Life's Big Questions", listOf(
            "Ecclesiastes 1-6 (Meaningless?)",
            "Luke 7-9 (Jesus' Power)",
            "Ecclesiastes 7-12 (Fear God)",
            "1 Corinthians 15 (Resurrection Hope)",
            "Song of Solomon 1-8 (Love's Beauty)",
            "Luke 10-12 (Priorities & Parables)",
            "Psalm 69-73 (When Life is Hard)"
        )),
        Week(22, "Kingdom Divided", listOf(
            "1 Kings 11-14 (Kingdom Splits)",
            "Luke 13-15 (Lost & Found)",
            "1 Kings 17-19 (Elijah's Ministry)",
            "2 Corinthians 1-4 (Treasure in Jars)",
            "1 Kings 21-22, 2 Kings 1-2 (Elijah & Elisha)",
            "Luke 16-18 (Kingdom Living)",
            "2 Kings 4-6 (Elisha's Miracles)"
        )),
        Week(23, "Kings & Prophets", listOf(
            "2 Kings 9-12 (Kings Rise & Fall)",
            "2 Corinthians 5-9 (New Creation)",
            "2 Kings 17-20 (Israel Falls, Judah Spared)",
            "Luke 19-21 (Jerusalem & End Times)",
            "2 Kings 22-25 (Josiah's Reform & Jerusalem Falls)",
            "2 Corinthians 10-13 (Paul's Defense)",
            "Psalm 74-78 (Remember God's Deeds)"
        )),
        Week(24, "Isaiah's Vision", listOf(
            "Isaiah 1-6 (Call & Vision)",
            "Luke 22-24 (Last Supper & Resurrection)",
            "Isaiah 9-12 (Messiah Promised)",
            "Galatians 1-3 (Gospel Freedom)",
            "Isaiah 40-44 (Comfort My People)",
            "Galatians 4-6 (Live by the Spirit)",
            "Isaiah 52-55 (Suffering Servant)"
        )),
        Week(25, "More of Isaiah", listOf(
            "Isaiah 58-62 (True Worship)",
            "Acts 1-3 (Church Begins)",
            "Isaiah 63-66 (New Heavens)",
            "Acts 4-6 (Early Church Growth)",
            "Jeremiah 1-4 (Jeremiah's Call)",
            "Acts 7-9 (Stephen & Saul)",
            "Psalm 79-85 (Restore Us)"
        )),
        Week(26, "Jeremiah's Warnings", listOf(
            "Jeremiah 7-11 (False Religion)",
            "Acts 10-12 (Gospel to Gentiles)",
            "Jeremiah 18-22 (Potter & Clay)",
            "Acts 13-15 (Paul's First Journey)",
            "Jeremiah 29-31 (Letter to Exiles & New Covenant)",
            "Acts 16-18 (Paul's Journeys)",
            "Jeremiah 36-39 (Scroll Burned & City Falls)"
        )),
        Week(27, "Captivity & Lament", listOf(
            "Jeremiah 50-52 (Babylon's Fall)",
            "Acts 19-21 (Ephesus & Jerusalem)",
            "Lamentations 1-5 (Jerusalem's Grief)",
            "Acts 22-24 (Paul Arrested)",
            "Ezekiel 1-6 (Ezekiel's Call & Visions)",
            "Acts 25-28 (Paul to Rome)",
            "Ezekiel 10-13 (Glory Departs)"
        )),
        Week(28, "Ezekiel's Visions", listOf(
            "Ezekiel 18-21 (Individual Responsibility)",
            "Romans 14-16 (Living Together)",
            "Ezekiel 33-37 (Dry Bones)",
            "1 Corinthians 6-9 (Body & Temple)",
            "Ezekiel 40-44 (New Temple Vision)",
            "1 Corinthians 16, 2 Corinthians 1-2 (Paul's Plans)",
            "Ezekiel 47-48 (River of Life)"
        )),
        Week(29, "Daniel & Friends", listOf(
            "Daniel 1-3 (Fiery Furnace)",
            "Revelation 1-3 (Letters to Churches)",
            "Daniel 4-6 (Lions' Den)",
            "Revelation 4-7 (Throne Room & Seals)",
            "Daniel 7-9 (Visions & Prayer)",
            "Revelation 8-11 (Trumpets)",
            "Daniel 10-12 (Final Vision)"
        )),
        Week(30, "Minor Prophets Part 1", listOf(
            "Hosea 1-7 (Unfaithful Love)",
            "Revelation 12-14 (War in Heaven)",
            "Hosea 8-14 (Return to God)",
            "Revelation 15-18 (Bowls of Wrath)",
            "Joel 1-3 (Day of the Lord)",
            "Revelation 19-20 (King of Kings)",
            "Amos 1-5 (Justice Rolls Down)"
        )),
        Week(31, "Minor Prophets Part 2", listOf(
            "Amos 6-9 (Basket of Fruit)",
            "Psalm 86-90 (Eternal God)",
            "Obadiah 1, Jonah 1-4 (Reluctant Prophet)",
            "Psalm 91-97 (God Reigns)",
            "Micah 1-7 (Walk Humbly)",
            "Psalm 98-104 (Sing to the Lord)",
            "Nahum 1-3, Habakkuk 1-3 (Justice Coming)"
        )),
        Week(32, "Minor Prophets Part 3", listOf(
            "Zephaniah 1-3 (The Great Day)",
            "Psalm 105-107 (Give Thanks)",
            "Haggai 1-2 (Rebuild the Temple)",
            "Psalm 108-115 (Praise the Lord)",
            "Zechariah 1-7 (Visions & Fasting)",
            "Psalm 116-119:88 (God's Word)",
            "Zechariah 9-14 (Messiah Coming)"
        )),
        Week(33, "Return from Exile", listOf(
            "Malachi 1-4 (Final Prophet)",
            "Psalm 119:89-176 (Longest Chapter!)",
            "Ezra 1-5 (Return & Rebuild)",
            "Psalm 120-129 (Songs of Ascent)",
            "Ezra 7-10 (Ezra's Mission)",
            "Psalm 130-139 (You Know Me)",
            "Nehemiah 1-4 (Walls Rebuilt)"
        )),
        Week(34, "Nehemiah's Leadership", listOf(
            "Nehemiah 5-9 (Revival & Confession)",
            "Psalm 140-145 (David's Final Psalms)",
            "Nehemiah 12-13 (Dedication & Reform)",
            "Psalm 146-150 (Praise Finale!)",
            "Esther 1-5 (For Such a Time)",
            "Job 1-5 (Why Do Good People Suffer?)",
            "Esther 6-10 (Deliverance)"
        )),
        Week(35, "Job's Suffering", listOf(
            "Job 6-11 (Friends Speak)",
            "1 Chronicles 1-5 (Genealogies)",
            "Job 19-23 (I Know My Redeemer Lives)",
            "1 Chronicles 10-14 (David's Kingdom)",
            "Job 32-37 (Elihu Speaks)",
            "1 Chronicles 22-26 (Temple Plans)",
            "Job 38-42 (God Answers)"
        )),
        Week(36, "Chronicles Review", listOf(
            "1 Chronicles 28-29, 2 Chronicles 1-3 (Solomon)",
            "2 Chronicles 6-9 (Temple & Glory)",
            "2 Chronicles 13-17 (Kings of Judah)",
            "2 Chronicles 20-24 (Jehoshaphat & Joash)",
            "2 Chronicles 29-32 (Hezekiah's Reforms)",
            "2 Chronicles 34-36 (Josiah & Exile)",
            "Catch-up or review favorite passages"
        )),
        Week(37, "Gospel Focus: Jesus' Ministry", listOf(
            "John 14-17 (Farewell Discourse)",
            "Luke 2:1-40, Matthew 1:18-2:23 (Birth Narratives)",
            "John 18-21 (Crucifixion & Resurrection)",
            "Mark 1-3 (Jesus Begins Ministry)",
            "Luke 4:1-30, Matthew 4:1-11 (Temptation & Preaching)",
            "Mark 4-6 (Parables & Miracles)",
            "Matthew 5-7 (Sermon on the Mount)"
        )),
        Week(38, "Pauline Epistles: Justification & Freedom", listOf(
            "Romans 1-4 (Righteousness by Faith)",
            "Romans 5-8 (Life in the Spirit)",
            "Galatians 1-3 (Justification by Faith)",
            "Galatians 4-6 (Freedom in Christ)",
            "Ephesians 1-3 (God's Plan)",
            "Ephesians 4-6 (Living a Holy Life)",
            "Philippians 1-4 (Joy in Suffering)"
        )),
        Week(39, "Pauline Epistles: Church Life & Second Coming", listOf(
            "Colossians 1-4 (Supremacy of Christ)",
            "1 Thessalonians 1-5 (The Lord's Return)",
            "2 Thessalonians 1-3 (End Times Correction)",
            "1 Corinthians 1-4 (Divisions & Wisdom)",
            "1 Corinthians 5-8 (Sexuality & Food)",
            "1 Corinthians 9-11 (Christian Liberty)",
            "1 Corinthians 12-14 (Spiritual Gifts)"
        )),
        Week(40, "Pauline Epistles: Resurrection & Defense", listOf(
            "1 Corinthians 15-16 (Resurrection)",
            "2 Corinthians 1-4 (Ministry & Comfort)",
            "2 Corinthians 5-9 (Reconciliation & Giving)",
            "2 Corinthians 10-13 (Paul's Defense)",
            "1 Timothy 1-3 (Church Leadership)",
            "1 Timothy 4-6 (False Teaching & Godliness)",
            "2 Timothy 1-4 (Endurance & Finish the Race)"
        )),
        Week(41, "General Epistles: Living Faith", listOf(
            "Titus 1-3, Philemon (Good Deeds & Partnership)",
            "Hebrews 1-4 (Jesus Superior to Angels/Moses)",
            "Hebrews 5-8 (Jesus the Great High Priest)",
            "Hebrews 9-10 (The Better Sacrifice)",
            "Hebrews 11-13 (Faith & Endurance)",
            "James 1-2 (True Religion)",
            "James 3-5 (Words & Wealth)"
        )),
        Week(42, "General Epistles: Hope & Warning", listOf(
            "1 Peter 1-2 (A Living Hope)",
            "1 Peter 3-5 (Suffering for Christ)",
            "2 Peter 1-3 (Growth & False Teachers)",
            "1 John 1-3 (Walk in Light & Love)",
            "1 John 4-5 (Testing Spirits & Assurance)",
            "2 John, 3 John, Jude (Truth, Hospitality, Warning)",
            "Catch-up or review favorite epistles"
        )),
        Week(43, "Prophetic Review: The Messiah", listOf(
            "Isaiah 7, 9, 11 (Messiah's Birth & Reign)",
            "Micah 5, Zechariah 9 (Messiah's Birthplace & Entry)",
            "Psalm 22, Isaiah 53 (Messiah's Suffering)",
            "Jeremiah 31, Ezekiel 36 (New Covenant)",
            "Malachi 3-4 (Forerunner & Return)",
            "Daniel 2, 7 (Kingdom Prophecies)",
            "Hosea 13-14, Joel 2 (Repentance & Restoration)"
        )),
        Week(44, "The Minor Prophets: Justice & Mercy", listOf(
            "Amos 5 (Seeking Justice)",
            "Jonah 3-4 (God's Compassion)",
            "Hosea 1-3 (God's Love)",
            "Micah 6 (Walk Humbly)",
            "Haggai 1 (Rebuilding Priority)",
            "Zechariah 4 (Not by Might, but Spirit)",
            "Zephaniah 3 (Singing God)"
        )),
        Week(45, "Wisdom Review: Life's Meaning", listOf(
            "Proverbs 3-4 (Trust & Knowledge)",
            "Proverbs 15-16 (Words & Plans)",
            "Ecclesiastes 1-3 (A Time for Everything)",
            "Ecclesiastes 11-12 (Remember Creator)",
            "Job 19, 38-39 (Trusting God's Power)",
            "Psalm 90, 139 (God's Eternity & Presence)",
            "Psalm 145 (Praise His Greatness)"
        )),
        Week(46, "Gospel Review: Salvation Story", listOf(
            "Luke 1:26-56 (Mary & Magnificat)",
            "Matthew 28 (The Great Commission)",
            "Mark 10:32-52 (Ransom)",
            "John 10 (Good Shepherd)",
            "John 19 (Crucifixion)",
            "Luke 24 (Road to Emmaus)",
            "Romans 8 (No Condemnation)"
        )),
        Week(47, "Acts Review: Mission of the Church", listOf(
            "Acts 2 (Pentecost)",
            "Acts 13 (First Missionary Journey Starts)",
            "Acts 16:6-40 (Philippi: Jail & Conversion)",
            "Acts 17 (Athens: Mars Hill)",
            "Acts 20:17-38 (Ephesus Elders)",
            "Acts 26 (Paul Before Agrippa)",
            "Catch-up or review early church history"
        )),
        Week(48, "Epistles Review: Practical Living", listOf(
            "Romans 12 (Spiritual Gifts & Love)",
            "1 Corinthians 13 (Love Chapter)",
            "Ephesians 5 (Marriage & Light)",
            "Colossians 3 (New Self)",
            "1 Timothy 6 (Contentment & Money)",
            "James 4 (Conflict & Submission)",
            "Hebrews 4:1-16 (Rest & High Priest)"
        )),
        Week(49, "Old Testament Review: Covenants", listOf(
            "Genesis 12:1-3, 15:1-21 (Abrahamic Covenant)",
            "Exodus 19-20 (Mosaic Covenant)",
            "2 Samuel 7 (Davidic Covenant)",
            "Jeremiah 31:31-34 (New Covenant)",
            "Genesis 6-9 (Noahic Covenant)",
            "Deuteronomy 6 (The Shema)",
            "Psalm 89 (Covenant Faithfulness)"
        )),
        Week(50, "New Testament Review: Jesus' Words", listOf(
            "Matthew 24-25 (Olivet Discourse)",
            "John 13 (Foot Washing)",
            "Luke 15 (Lost Parables)",
            "Matthew 13 (Kingdom Parables)",
            "Mark 12 (Greatest Commandment)",
            "John 14 (I Am the Way)",
            "Luke 10:25-37 (Good Samaritan)"
        )),
        Week(51, "Final Reflections: Revelation & New Creation", listOf(
            "Revelation 1:1-20 (Vision of Christ)",
            "Revelation 5 (Worthy is the Lamb)",
            "Revelation 19 (Marriage Supper)",
            "Revelation 20 (Millennium & Judgment)",
            "Revelation 21 (New Jerusalem)",
            "Revelation 22 (River of Life)",
            "Catch-up or read a favorite book again"
        )),
        Week(52, "Reflection & Next Steps", listOf(
            "Read your favorite book from the Old Testament",
            "Read your favorite book from the New Testament",
            "Write down 5 key themes you learned this year",
            "Journal about how your faith has changed",
            "Share your favorite verse with a friend",
            "Plan your reading strategy for next year",
            "Celebrate finishing the Bible in a year!"
        ))
    )
}