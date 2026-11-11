# My Money Manager — Native Android (Kotlin + Jetpack Compose)

This is a native Android Studio project based on the supplied My Money Manager HTML specification. It uses Jetpack Compose UI (not a WebView), local SharedPreferences JSON persistence, Indian Rupee formatting, and native Compose transitions.

## Implemented
- Splash / Get Started flow
- Home dashboard with balance visibility, income/expense totals, monthly budget progress, savings progress and recent entries
- Add, edit and delete income/expense transactions
- Search and filter transaction history
- Animated category icons (shopping cart, food, bills, transport, salary, etc.)
- Expense category report with donut chart and category bars
- Savings goals, add saved amount, delete goals
- Profile and card-holder settings; dark/light theme
- Monthly budget setting
- Monthly recurring transactions, applied once per month
- 4–12 digit app PIN with SHA-256 hash
- JSON backup and restore through Android document picker
- Reset application data

## Build APK
Open this folder in Android Studio (Ladybug or newer), allow Gradle sync, install Android SDK Platform 35 and Build Tools, then select **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Command line from a machine with Android SDK and Gradle available:

```bash
gradle assembleDebug
```

Package: `com.example.mymoneymanager`  
Version: `1.0` (versionCode 1)  
Minimum Android: 6.0 (API 23)

## Important
This execution environment does not contain Android SDK / `android.jar` or Gradle, so an APK cannot be compiled or emulator-tested here. The project source is provided for Android Studio build; do not treat this source ZIP as an APK. Review and run it on an Android device/emulator before release.
