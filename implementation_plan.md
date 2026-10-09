# План реализации: Переключение языка интерфейса в Настройках (Interface Language Switching)

## 1. Обзор задачи и требования
Задача: Добавить переключение языка интерфейса приложения в разделе «Настройки» («Настройки» ➔ «Язык интерфейса») с поддержкой 7 языков и системного языка по умолчанию:
1. 🇷🇺 **Русский** (`ru`)
2. 🇬🇧 **Английский** (`en`)
3. 🇩🇪 **Немецкий** (`de`)
4. 🇺🇦 **Украинский** (`uk`)
5. 🇸🇦 **Арабский** (`ar`, с поддержкой RTL)
6. 🇦🇫 **Дари** (`fa`, с поддержкой RTL)
7. 🇷🇴 **Румынский** (`ro`)
8. ⚙️ **Системный по умолчанию** (`""` / `system`)

### Ключевые требования:
- **Полная локализация строковых ресурсов**: создание исчерпывающих файлов `strings.xml` для всех 8 директорий ресурсов (`values`, `values-ru`, `values-en`, `values-de`, `values-uk`, `values-ar`, `values-fa`, `values-ro`) без незаполненных ключей.
- **Интерфейс настроек**: выпадающий список (Dropdown) с нативными названиями языков и иконкой выбора (Check).
- **Динамическое переключение на лету**: обновление Compose `LocalConfiguration`, `LocalContext`, `LocalLayoutDirection` без перезапуска приложения и без мерцания.
- **RTL макет**: автоматическое отзеркаливание верстки справа налево для арабского языка (`ar`) и дари (`fa`).
- **Синхронизация с Android 13+ (API 33+)**: использование системного `LocaleManager.applicationLocales` и декларация в `res/xml/locales_config.xml`.
- **Персистентность**: сохранение выбранного языка в `SettingsRepository` через DataStore Preferences.
- **Отсутствие регрессий**: сохранение работоспособности всех 163+ unit-тестов, мультиязычных провайдеров перевода и карточек SM-2.

---

## 2. Архитектура и реализованные компоненты

### 2.1. Доменная модель `AppLanguage`
- Файл: `app/src/main/java/app/deutschlern/domain/model/AppLanguage.kt`
- Содержит перечисление с атрибутами `code`, `displayName`, `nativeName`, `isRtl`, `bcp47Tag`.
- Метод `fromCode(code: String?)` устойчив к нечувствительности к регистру, пустым строкам, префиксам BCP-47 и неизвестным кодам.

### 2.2. Хранилище настроек `SettingsRepository` & `SettingsRepositoryImpl`
- Добавлен ключ `KEY_APP_LANGUAGE` (`app_language_code`).
- Методы:
  - `setAppLanguage(languageCode: String)`
  - Свойство `UserSettings.appLanguageCode` и геттер `selectedAppLanguage: AppLanguage`.

### 2.3. Интеграция в Activity и Compose
- `app/src/main/java/app/deutschlern/MainActivity.kt`:
  - Внедрение `SettingsRepository` через Hilt.
  - Двусторонняя синхронизация с Android 13+ (API 33+) Per-App Language Preferences (`LocaleManager.applicationLocales`):
    - При холодном старте, если в DataStore настройка ещё не задана, а в системных настройках Android 13+ пользователь уже выбрал язык приложения, он автоматически импортируется в DataStore.
    - Ожидание завершения загрузки `Settings` из DataStore перед запуском side-effects, что полностью исключает ложный сброс системной локали в пустой список на холодном старте.
  - Динамическое создание локализованного `Configuration` и обёрнутого `Context` (через `createConfigurationContext`) как для кастомных локалей, так и для системной (`SYSTEM`), с корректным сбросом `Locale.setDefault(sysLocale)`.
  - Оборачивание всего дерева Compose в:
    ```kotlin
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection
    ) {
        DeutschLernTheme {
            DeutschLernApp()
        }
    }
    ```
  - В `AndroidManifest.xml` для `MainActivity` добавлено `android:configChanges="locale|layoutDirection"`, что предотвращает уничтожение и перезапуск Activity при смене языка, сохраняя положение пользователя на экране «Настройки» и состояние экрана.

### 2.4. Декларация конфигурации локалей Android 13+
- Файл: `app/src/main/res/xml/locales_config.xml`:
  - Задекларированы теги `ru`, `en`, `de`, `uk`, `ar`, `fa`, `ro`.
- Файл: `app/src/main/AndroidManifest.xml`:
  - Добавлены атрибуты `android:localeConfig="@xml/locales_config"` и `android:configChanges="locale|layoutDirection"`.

### 2.5. UI экрана настроек
- Файл: `app/src/main/java/app/deutschlern/ui/settings/SettingsScreen.kt`:
  - Добавлен компонент `AppLanguageSection` в секцию «Общие настройки».
  - Отображает карточку с заголовком, описанием, текущим языком с нативным названием и выпадающим списком (`DropdownMenu` с `widthIn(min = 280.dp)`) со всеми поддерживаемыми языками.
  - Каждая строка меню содержит нативное название языка и галочку напротив выбранного.
  - Обновлены векторные иконки внешних ссылок на `Icons.AutoMirrored.Filled.OpenInNew` для корректного отображения в RTL-макетах.

### 2.6. Локализация строковых ресурсов (8 локалей)
- `app/src/main/res/values/strings.xml` (Базовый / Русский)
- `app/src/main/res/values-ru/strings.xml` (Русский)
- `app/src/main/res/values-en/strings.xml` (Английский)
- `app/src/main/res/values-de/strings.xml` (Немецкий)
- `app/src/main/res/values-uk/strings.xml` (Украинский)
- `app/src/main/res/values-ar/strings.xml` (Арабский, RTL)
- `app/src/main/res/values-fa/strings.xml` (Дари, RTL)
- `app/src/main/res/values-ro/strings.xml` (Румынский)
- Все 151 строковых ключей и их плейсхолдеры полностью синхронизированы во всех 8 локалях.

---

## 3. Тестирование и верификация

### 3.1. Автоматизированные тесты
- Добавлен `SettingsRepositoryTest.kt`:
  - Проверка значений по умолчанию (`AppLanguage.SYSTEM`, пустой код, `isRtl = false`).
  - Проверка маппинга всех кодов языков в `UserSettings.selectedAppLanguage`.
  - Проверка RTL-флагов для арабского и дари.
  - Проверка устойчивости `AppLanguage.fromCode` к регистру, пробелам, null и BCP-47 префиксам.
  - Проверка валидности атрибутов, BCP-47 тегов и отображаемых имен всех элементов `AppLanguage`.
- Расширен `SettingsViewModelTest.kt`:
  - Тест загрузки `selectedAppLanguageCode` из настроек.
  - Тест вызова `settingsRepository.setAppLanguage(...)` при выборе нового языка (`ar`, `fa`, `ro`, системный `""`).
- Результат прогона всего тестового набора (`.\gradlew.bat testDebugUnitTest`):
  - **173 теста выполнено, 0 ошибок, 0 пропусков (100% SUCCESSFUL)**.

### 3.2. Сборка и установка APK
- Команда сборки: `.\gradlew.bat testDebugUnitTest assembleDebug` -> `BUILD SUCCESSFUL`.
- APK файл: `app/build/outputs/apk/debug/app-debug.apk` (84 MB).
- Установка на подключенное устройство `XGRC79QSXCD6GEA6` через ADB: `adb install -r "app/build/outputs/apk/debug/app-debug.apk"` -> `Performing Streamed Install -> Success`.
- Запуск приложения: `adb shell am start -n app.deutschlern/.MainActivity` -> `Success`.

### 3.3. Ручная проверка на устройстве
1. Открыть вкладку **«Перевод»**, нажать на значок шестерёнки вверху («Настройки»).
2. В первой секции **«Общие настройки»** найти пункт **«Язык интерфейса»**.
3. Нажать на селектор и последовательно проверить:
   - **English**: все вкладки нижней панели («Translate», «Words», «Review») и настройки отображаются на английском.
   - **Deutsch**: надписи меняются на «Übersetzen», «Wörter», «Wiederholung».
   - **Українська**: надписи меняются на «Переклад», «Слова», «Повторення».
   - **العربية**: интерфейс мгновенно переключается на арабский язык, а все элементы и табы зеркалируются справа налево (RTL).
   - **دری**: интерфейс переключается на дари с сохранением RTL-ориентации.
   - **Română**: интерфейс переключается на румынский язык.
   - **Системный**: интерфейс подстраивается под системный язык устройства.
4. Перезапустить приложение (`adb shell am force-stop app.deutschlern` и затем открыть заново) — выбранный язык сохраняется из DataStore на холодном старте, системные локали не сбрасываются.
5. Проверить переключение системного языка через adb:
   - `adb shell cmd locale set-app-locales app.deutschlern --locales ar`
   - Перезапустить приложение — арабская локаль сохраняется и отображается.
