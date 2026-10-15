# Dialogue Recording & Detail Investigation Report

## Symptoms
1. **Dialogue Loss During Recording**: Recognizer appears to "hit a limit" during dialogues with natural pauses; subsequent speech is lost.
2. **Crash When Opening Saved Dialogue**: Opening a saved dialogue crashes the application on real Android 16 (Xiaomi POCO/Redmi `rodin`, API 36).

---

## Stage A Findings & Fixes

### 1. Saved Dialogue Opening Crash
- **Status**: Confirmed by log & code reading.
- **Log Evidence**:
  ```
  java.lang.ClassCastException: java.lang.Long cannot be cast to java.lang.String
      at app.deutschlern.ui.dialog.DialogDetailViewModel.<init>(DialogDetailViewModel.kt:57)
  ```
- **Root Cause**:
  `AppNavigation.kt` configures `navArgument("dialogId") { type = NavType.LongType }` and `navArgument("taskId") { type = NavType.LongType }`.
  `DialogDetailViewModel` evaluated `savedStateHandle.get<String>("dialogId")?.toLongOrNull()` first. In Kotlin, this generates a bytecode checkcast to `java.lang.String`. Since Compose Navigation stores the argument as `java.lang.Long`, the cast threw an immediate `ClassCastException` before reaching the fallback.
  The same flaw existed in `DialogTaskViewModel` for both `dialogId` and `taskId`.
- **Audit of `savedStateHandle` across the entire codebase**:
  - `DialogDetailViewModel`: `dialogId` -> **FIXED** with `getNavigationLong("dialogId")`.
  - `DialogTaskViewModel`: `dialogId`, `taskId` -> **FIXED** with `getNavigationLong`.
  - `CheckViewModel`: `KEY_CHECK_INPUT` (`"saved_check_input"`, `String`, internal state preservation, type matches).
  - `TranslateViewModel`: `KEY_INPUT_TEXT` (`"saved_input_text"`, `String`, internal state preservation, type matches).
  - `UpdateViewModel`: `"wifi_only"` (`Boolean`, internal state, type matches).
  - `AppNavigation.kt`: confirmed no other destinations use `navArgument`.
- **Solution**:
  Introduced `SavedStateHandle.getNavigationLong(key: String, defaultValue: Long = 0L)` in [`NavigationExtensions.kt`](file:///e:/Новая%20папка/app/src/main/java/app/deutschlern/ui/navigation/NavigationExtensions.kt), safely extracting `Long`, `Number`, or parsing `String` without risking `ClassCastException`.
- **Unit Tests**:
  - [`NavigationExtensionsTest.kt`](file:///e:/Новая%20папка/app/src/test/java/app/deutschlern/ui/navigation/NavigationExtensionsTest.kt): verified `Long`, `String`, `Int`, invalid `String`, and missing keys.
  - [`DialogDetailViewModelTest.kt`](file:///e:/Новая%20папка/app/src/test/java/app/deutschlern/ui/dialog/DialogDetailViewModelTest.kt): verified instantiation with `Long` argument, `String` argument fallback, missing argument default.
  - [`DialogTaskViewModelTest.kt`](file:///e:/Новая%20папка/app/src/test/java/app/deutschlern/ui/dialog/DialogTaskViewModelTest.kt): verified instantiation with `Long` arguments (`dialogId` and `taskId`), `String` argument fallback, missing arguments default.
- **Device Verification**:
  Verified on physical device (Xiaomi POCO/Redmi `rodin`, Android 16 API 36):
  1. **Path 1 (List -> Detail)**: Opened saved dialogue "Café Gespräch" (id=1); all segments and duration rendered with zero crashes.
  2. **Path 3 (Detail -> Tasks)**: Opened MCQ task ("Was möchte der Kunde?"); task options rendered with zero crashes.
  3. **Path 4 (Process Death / Recreation)**: Recreated activity scenario and killed process on Detail screen; state restored cleanly without crashes.
  4. **Path 2 (Recording Screen)**: Navigated to `DialogRecordingScreen`; UI and recording timer rendered with zero crashes.

---

## Classification of Recording Hypotheses (Stage B Baseline)
- **3-Second Silence Timer with `cancel()` killing STT**: Confirmed by code reading.
- **`EXTRA_SEGMENTED_SESSION` String instead of Boolean**: Confirmed by code reading (`AndroidSpeechEngine.kt:119`).
- **Rapid restart / Throttling / API 36 error handling**: Hypothesis to be validated via 15+ min session logging in Stage B.
- **Uncommitted last partial on session termination**: Confirmed by code reading.
- **Screen-off / Doze / WakeLock**: Hypothesis to be verified with logcat in Stage B.
- **Compose rendering / CursorWindow limit**: Hypothesis to be measured with synthetic load test in Stage D.
