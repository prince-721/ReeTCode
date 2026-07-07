# 🎬 Reel Tracker

An Android app that tracks how many reels/shorts you watch on Instagram, Snapchat, and YouTube, automatically blocks them when you hit your daily limit, and allows you to earn scroll time back by solving coding problems on LeetCode, CodeChef, and GeeksforGeeks, or restrict access using customized Focus Sessions.

---

## Features

| Feature | Details |
|---|---|
| **Scroll Detection** | Uses `AccessibilityService` to detect reel/short scrolls in Instagram, Snapchat & YouTube |
| **Daily Limit** | Configurable limit (default: 50). Slider on home screen + Settings |
| **Auto-Block** | When limit is hit, a fullscreen overlay blocks the three apps for 6 hours |
| **Countdown Timer** | Blocking screen shows live `Xh Ym Zs remaining` countdown |
| **Emergency Unlock** | 60-second forced wait before manual override (discourages impulse unlocking) |
| **Streak Tracker** | Counts consecutive days you stayed under your limit 🔥 |
| **History** | 7-day bar chart + per-day breakdown with Instagram/Snapchat/YouTube split |
| **Live Notification** | Persistent foreground notification: *"Reels today: 34/50"* |
| **Daily Reset** | Alarm fires at midnight to reset the counter automatically |
| **Boot Restart** | Service restarts automatically after phone reboot |
| **Dark Mode** | Full dark/light theme support |
| **Onboarding** | First-launch walkthrough explaining each required permission |
| **Code to Unlock** | Solve programming problems on **LeetCode, CodeChef, or GeeksforGeeks** to earn custom scroll time (e.g. 30 mins per problem) or fully unlock early! |
| **Verification System** | Secure verification of coding profiles using custom codes (`ReetCode-XXXX`) in the user's bio/profile information to prevent cheating. |
| **Floating Countdown Overlay** | A premium floating overlay pill (`Unlocked: MM:SS`) at the top of the screen during temporary unlock periods when using blocked apps. |
| **Focus Mode** | Custom focus sessions allowing users to restrict access to a list of apps (or allow only safe apps) for a specified duration, bypassable only by completing the session or coding targets. |

---

## Project Structure

```
app/src/main/kotlin/com/reeltracker/
├── MainActivity.kt                          # Entry point
├── ReelTrackerApp.kt                        # Application class, notification channels
├── AppContainer.kt                          # Simple DI container
│
├── data/
│   ├── UserPreferences.kt                   # DataStore preferences
│   ├── FocusedModeRepository.kt             # Focused mode SharedPreferences repository
│   ├── entities/
│   │   ├── DailyReelCount.kt                # Room entity: per-day counts
│   │   ├── BlockSession.kt                  # Room entity: block sessions
│   │   ├── FocusMode.kt                     # Room entity: focus modes
│   │   └── CodingPlatformConfig.kt          # Room entity: coding platform usernames/verification
│   ├── dao/
│   │   ├── DailyReelCountDao.kt             # DAO: daily counts CRUD
│   │   ├── BlockSessionDao.kt               # DAO: block sessions CRUD
│   │   ├── FocusModeDao.kt                  # DAO: focus modes CRUD
│   │   └── CodingPlatformConfigDao.kt       # DAO: coding configs CRUD
│   ├── database/
│   │   └── ReelTrackerDatabase.kt           # Room database definition
│   └── repository/
│       └── ReelTrackerRepository.kt         # Single source of truth
│
├── service/
│   ├── ReelAccessibilityService.kt          # Detects reel scrolls, displays overlay pill / fullscreen blockers
│   ├── ReelTrackerService.kt                # Foreground service, orchestrates everything
│   └── CodingPlatformService.kt             # Service to verify profiles & check submissions (LeetCode, CodeChef, GFG)
│
├── receiver/
│   ├── BootReceiver.kt                      # Restarts service after reboot
│   ├── MidnightResetReceiver.kt             # Fires at midnight to reset counter
│   ├── UnlockReceiver.kt                    # Handles unlock broadcast
│   └── TrackerDeviceAdminReceiver.kt        # Device administrator receiver for anti-uninstall security
│
├── viewmodel/
│   ├── ReelTrackerViewModel.kt              # MVVM ViewModel with StateFlow
│   ├── ReelTrackerViewModelFactory.kt       # ViewModel factory
│   └── CodingUnlockViewModel.kt             # Handles state for platforms verification & problem count checks
│
└── ui/
    ├── theme/Theme.kt                       # Material3 dark/light color scheme
    ├── navigation/NavGraph.kt               # Compose Navigation setup
    ├── components/
    │   ├── CircularProgressRing.kt          # Animated ring (current/total)
    │   └── BarChart.kt                      # Weekly bar chart
    └── screens/
        ├── OnboardingScreen.kt              # First-launch permission walkthrough
        ├── HomeScreen.kt                    # Main screen: ring, streak, slider
        ├── HistoryScreen.kt                 # 7-day history + breakdown
        ├── SettingsScreen.kt                # Limits, app toggles, permissions, coding profile verification
        ├── BlockingActivity.kt              # Fullscreen block screen + emergency unlock
        ├── FocusedModeInfoActivity.kt       # Focus mode blocker details
        └── CodeToUnlockSheet.kt             # Bottom sheet to check/claim coding unlock credits
```

---

## Setup & Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 35
- Kotlin 2.0+
- A physical Android device (API 26+) — the Accessibility Service does **not** work on emulators

### Steps

1. **Clone / open** the project in Android Studio.

2. **Set your SDK path** in `local.properties`:
   ```
   sdk.dir=/Users/yourname/Library/Android/sdk
   ```

3. **Build and install:**
   ```bash
   ./gradlew installDebug
   ```

4. **Grant permissions** (the onboarding screen walks you through each one):

   | Permission | Why |
   |---|---|
   | Accessibility Service | Detects scroll events in target apps |
   | Draw Over Other Apps | Shows the fullscreen blocking screen |
   | Post Notifications | Shows the persistent "Reels today: X/Y" notification |
   | Receive Boot Completed | Restarts tracking after reboot |

---

## How Reel Detection Works

The `ReelAccessibilityService` monitors `TYPE_VIEW_SCROLLED` and `TYPE_WINDOW_CONTENT_CHANGED` events restricted to three packages:

```
com.instagram.android
com.snapchat.android
com.google.android.youtube
```

For each scroll event it:
1. Checks the view ID resource name of the scrolled node (and up to 5 ancestor nodes) against known reel-section IDs per app.
2. Applies a **600ms debounce** so a single fast-scroll doesn't count as many reels.
3. Broadcasts `ACTION_REEL_DETECTED` to `ReelTrackerService` if a match is found.

**Known reel-section view IDs used:**

| App | Detected IDs |
|---|---|
| Instagram | `clips_viewer_container`, `reel_viewer_fragment`, `clips_recycler_view`, … |
| YouTube | `shorts_container`, `reel_recycler_view`, `reel_player_page`, … |
| Snapchat | `stories_feed_recycler_view`, `spotlight_container`, `snap_view`, … |

> **Note:** These view IDs are extracted from the apps' current UI layouts. If Instagram/YouTube/Snapchat update their internal view IDs, you may need to update `ReelAccessibilityService.kt`. Use Android Studio's Layout Inspector or `adb shell uiautomator dump` to find new IDs.

---

## Architecture

```
AccessibilityService
       │  (broadcast: ACTION_REEL_DETECTED)
       ▼
ReelTrackerService (Foreground)
       │  (Room writes + Flow reads)
       ▼
ReelTrackerRepository
       │  (Flow emissions)
       ▼
ReelTrackerViewModel  ──StateFlow──►  Compose UI
       │
       └──► BlockingActivity (when limit hit)
```

**Tech stack:**
- Language: **Kotlin**
- UI: **Jetpack Compose + Material 3**
- Database: **Room 2.6**
- Preferences: **DataStore**
- Background: **Foreground Service** (specialUse type)
- Architecture: **MVVM** with `StateFlow` + `collectAsStateWithLifecycle`
- No third-party libraries beyond Jetpack

---

## Configuration

All user-configurable values are stored in **DataStore** (`UserPreferences`):

| Key | Default | Description |
|---|---|---|
| `daily_limit` | 50 | Max reels per day before blocking |
| `tracking_enabled` | true | Master toggle for the service |
| `track_instagram` | true | Count Instagram Reels |
| `track_snapchat` | true | Count Snapchat Stories/Spotlight |
| `track_youtube` | true | Count YouTube Shorts |
| `block_duration_hours` | 6 | Hours to block after limit hit |
| `has_completed_onboarding` | false | Shown first-launch guide |

---

## Notes & Limitations

- **Accessibility Service**: Android's battery optimisation may kill the service on some OEMs (Xiaomi, Huawei, OnePlus). Guide users to whitelist the app in battery settings.
- **View ID stability**: Instagram, Snapchat and YouTube change their internal layouts with app updates. View IDs may need updating after major app updates.
- **Overlay blocking**: The blocking screen uses a full-screen Activity (`FLAG_ACTIVITY_NEW_TASK`) rather than a `TYPE_APPLICATION_OVERLAY` window, which is more reliable across Android versions and doesn't require the deprecated `WindowManager` overlay approach.
- **`SCHEDULE_EXACT_ALARM`**: On Android 12+ the midnight reset uses `setRepeating` (inexact). For exact midnight resets, request `SCHEDULE_EXACT_ALARM` permission and use `setExactAndAllowWhileIdle`.
