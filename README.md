# BibleIn52

BibleIn52 is a comprehensive Bible reading tracker app designed to help users complete the Bible in 52 weeks. It combines a structured reading plan with progress tracking, statistics, calendar view, and reminders to encourage daily engagement.

## Features

- **Structured Weekly Reading Plan**: Follow a complete one-year Bible reading schedule with daily passages covering both Old and New Testament.
- **Daily Completion Tracking**: Mark each day’s reading as completed with a simple tap.
- **Visual Progress Indicators**: Progress bars and statistics show percentage completion, books completed, total chapters read, and ongoing readings.
- **Streak Counter**: See your consecutive day streak to stay motivated.
- **Calendar View**: A monthly calendar shows which days have completed readings, highlighting today and completed days.
- **Daily Reminders**: Schedule notifications to remind you to read at your preferred time. You can enable/disable reminders and choose morning, noon, or evening times.
- **Persistent Data Storage**: All reading progress and settings are saved locally using `SharedPreferences`.
- **Interactive UI**: Tappable readings, color-coded progress indicators, and simple navigation across Reading Plan, Calendar, and Settings tabs.
- **Stat Cards**: Quick view of your reading statistics: day streak, percentage complete, books done, and total chapters.
- **Responsive Design**: Adapted for different screen sizes with Compose layouts.
- **Customizable Reminder Times**: Predefined time buttons to set reminders for 9:00 AM, 12:00 PM, or 8:00 PM.
- **Notification Handling**: Uses `AlarmManager` and `BroadcastReceiver` for daily notifications.