# DeutschLern — План реализации и архитектура проекта

## 1. Архитектурный обзор (Architecture Overview)

Приложение строится строго по принципам Clean Architecture + MVVM + Unidirectional Data Flow (UDF) в рамках единого модуля `app`:
- **Пакет приложения**: `app.deutschlern`
- **Целевая платформа**: Android (minSdk 26, compileSdk/targetSdk: latest stable, currently API 35)
- **Язык и среда**: Kotlin 2.0+, Kotlin Coroutines + Flow, Jetpack Compose + Material 3, Navigation Compose.
- **Внедрение зависимостей**: Dagger Hilt с KSP (без устаревшего kapt).
- **Сетевой уровень**: Retrofit 2 + OkHttp 4 + kotlinx.serialization (JSON).
- **Локальное хранение**: Room 2.6+ (KSP) + Jetpack DataStore (Preferences).
- **Тестирование**: JUnit 4 / JUnit 5, MockK, Turbine, Room In-Memory DB.
- **Без сторонней аналитики, рекламы, авторизации и собственного бэкенда**.

### Структура пакетов (`app/src/main/java/app/deutschlern/`):
```text
app.deutschlern/
├── data/
│   ├── local/
│   │   ├── dao/             # WordDao, SessionDao, TextTranslationDao, CacheDao, ReviewDao
│   │   ├── entity/          # WordEntryEntity, SessionEntity, TextTranslationEntity, CacheEntryEntity, ReviewStateEntity
│   │   ├── converter/       # Room TypeConverters (Meanings, VerbForms, Synonyms, Collocations)
│   │   ├── AppDatabase.kt   # RoomDatabase (exportSchema = false)
│   │   └── preferences/     # AppPreferences (DataStore)
│   ├── remote/
│   │   ├── api/             # GeminiApiService (REST generateContent)
│   │   ├── dto/             # GeminiRequestDto, GeminiResponseDto, WordTranslationDto, MeaningDto, VerbFormsDto, CollocationDto
│   │   ├── interceptor/     # GeminiAuthInterceptor (добавление заголовка x-goog-api-key)
│   │   └── datasource/      # GeminiTranslationDataSource (инкапсулирует вызовы API)
│   └── repository/          # TranslationRepositoryImpl, VocabularyRepositoryImpl, ReviewRepositoryImpl
├── domain/
│   ├── model/               # WordEntry, Meaning, VerbForms, GrammarInfo, Collocation, Session, ReviewCard, Sm2State
│   ├── repository/          # TranslationRepository, VocabularyRepository, ReviewRepository
│   ├── usecase/             # TranslateWordUseCase, TranslateTextUseCase, SaveWordUseCase,
│   │                        # GetDueCardsUseCase, ReviewCardUseCase, DetectLanguageUseCase, DetectModeUseCase
│   └── sm2/                 # Sm2Scheduler (чистая бизнес-логика алгоритма SM-2)
├── di/                      # DatabaseModule, NetworkModule, RepositoryModule, CoroutineDispatchersModule
├── ui/
│   ├── components/          # WordCard, MeaningRow, HairlineDivider, SpeakerButton, MinimalButton, StatusBadge
│   ├── navigation/          # AppNavigation, Screen (Translate, Words, Review)
│   ├── review/              # ReviewScreen, ReviewViewModel, ReviewUiState, FlashcardComponent
│   ├── theme/               # Color, Typography, Theme, Shape
│   ├── translate/           # TranslateScreen, TranslateViewModel, TranslateUiState, TranslateUiEvent
│   └── words/               # WordsScreen, WordsViewModel, WordsUiState, WordDetailBottomSheet
└── util/                    # LanguageDetector, ModeDetector, TextNormalizer, CsvExporter, TtsManager, Prompts
```

---

## 2. Спецификация слоев данных и сущностей Room

### 2.1. Таблицы базы данных

1. **`words` (`WordEntryEntity`)**:
   - `id`: `Long` (Primary Key, autogenerate = true)
   - `lemma`: `String` (нормализованная форма: инфинитив для глаголов, им. пад. ед. ч. для сущ.; нормализуется через trim и Unicode NFC)
   - `pos`: `String` (noun, verb, adjective, adverb, preposition, conjunction, pronoun, particle, phrase, other)
   - `article`: `String?` (der, die, das или null для не-существительных)
   - `plural`: `String?` (только для существительных, иначе null)
   - `genitive`: `String?` (только для существительных, иначе null)
   - `grammarJson`: `String?` (содержит: `verb_forms`, `government`, `comparative`, `superlative`)
   - `meaningsJson`: `String` (JSON массив значений с примерами B2, пометами и best_match)
   - `synonymsJson`: `String` (JSON массив синонимов `List<String>`)
   - `collocationsJson`: `String` (JSON массив коллокаций `List<CollocationDto>`)
   - `selectedMeaningIndex`: `Int?` (индекс выбранного пользователем значения; null по умолчанию)
   - `timesTranslated`: `Int` (счетчик повторных запросов, по умолчанию 1)
   - `firstSeenAt`: `Long` (timestamp в миллисекундах)
   - `lastSeenAt`: `Long` (timestamp последнего запроса)
   - `status`: `String` (`NEW`, `LEARNING`, `KNOWN`)
   - **Индексы**: Уникальный составной индекс `UNIQUE(lemma, pos)` для защиты от дубликатов. Перед проверкой уникальности `lemma` обязательно нормализуется (trim, Unicode NFC).
   - **Логика Upsert и сессий**:
     - При повторном переводе слова `words.sessionId` **НЕ перезаписывается**.
     - `timesTranslated` увеличивается на 1.
     - `lastSeenAt` обновляется на текущее время.
     - Прогресс SM-2 в `review_states` **не сбрасывается и остается нетронутым**.
     - В таблицу связей `session_words` добавляется запись с текущей активной сессией `(sessionId, wordId, translatedAt)`, благодаря чему слово отображается в каждом уроке, где оно переводилось.
     - **RU → DE обработка**: Автосохранение выполняется **только** для запросов `DE → RU`. Для `RU → DE` для каждого немецкого эквивалента отображается кнопка «Сохранить», сохраняющая немецкую запись (`lemma` = немецкое слово, артикль, перевод = исходный русский запрос). Русское слово ни при каких условиях не сохраняется как `lemma`.

2. **`sessions` (`SessionEntity`)**:
   - `id`: `Long` (Primary Key, autogenerate = true)
   - `title`: `String` (например, "Урок, 12 окт")
   - `date`: `Long` (timestamp начала суток)
   - `isManual`: `Boolean` (создана ли сессия вручную кнопкой «Новый урок»)

3. **`session_words` (`SessionWordCrossRef`)**:
   - `sessionId`: `Long` (Foreign Key -> `sessions.id` с `CASCADE ON DELETE`)
   - `wordId`: `Long` (Foreign Key -> `words.id` с `CASCADE ON DELETE`)
   - `translatedAt`: `Long` (timestamp добавления слова в урок)
   - **Первичный ключ / Индекс**: Уникальный составной ключ `UNIQUE(sessionId, wordId)`. Обеспечивает появление слова во всех уроках, в которых оно переводилось, без перезаписи исходной сессии первого добавления.

4. **`text_translations` (`TextTranslationEntity`)**:
   - `id`: `Long` (Primary Key, autogenerate = true)
   - `source`: `String` (исходный текст, до 5000 символов)
   - `result`: `String` (переведенный текст с сохранением абзацев)
   - `direction`: `String` ("DE_RU" или "RU_DE")
   - `createdAt`: `Long`

5. **`review_states` (`ReviewStateEntity`)**:
   - `wordId`: `Long` (Primary Key, Foreign Key -> `words.id` с `CASCADE ON DELETE`)
   - `easeFactor`: `Double` (начальное значение 2.5, минимум 1.3, максимум 3.0)
   - `intervalDays`: `Int` (начальное значение 0)
   - `repetitions`: `Int` (начальное значение 0)
   - `dueAt`: `Long` (timestamp срока следующего повторения)

6. **`translation_cache` (`CacheEntryEntity`)**:
   - `cacheKey`: `String` (Primary Key, SHA-256 хэш строки: `modelName + ":" + promptVersion + ":" + mode + ":" + direction + ":" + normalizedText + ":" + (context ?: "")`)
   - **Нормализация текста**: Исходный текст перед хэшированием очищается от краевых пробелов (trim), множественные пробельные символы схлопываются в один (collapse whitespace), и применяется Unicode NFC-нормализация.
   - `responseJson`: `String`
   - `createdAt`: `Long`
   - **TTL**: 90 дней (записи старше 90 дней очищаются фоново при старте).
   - **Регистрозависимость**: Для немецкого языка регистр в `normalizedText` **сохраняется**, так как капитализация определяет часть речи и значение ("Schloss" — замок/сущ. vs "schloss" — закрыл/глаг.).

---

## 3. Интеграция с Gemini API

### 3.1. Безопасность и архитектурная изоляция
- Источник данных `GeminiTranslationDataSource` реализует внутренний интерфейс источника и изолирован за `TranslationRepository`. Это гарантирует возможность бесшовной замены на Firebase AI Logic SDK без модификации UI или доменного слоя.
- API-ключ считывается из `local.properties` (`GEMINI_API_KEY`) во время сборки и внедряется в `BuildConfig.GEMINI_API_KEY`.
- Файл `local.properties` включен в `.gitignore`. Ключ ни при каких условиях не логируется и не хардкодится.
- Для передачи ключа используется безопасный HTTP-заголовок `x-goog-api-key: {apiKey}` вместо query-параметра в URL (что исключает утечку ключа в логи сетевых запросов и прокси).

### 3.2. Endpoint и конфигурация модели
- **URL**: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- **Модель по умолчанию**: `gemini-3.5-flash` (актуальная GA Flash-модель согласно официальной странице ai.google.dev/gemini-api/docs/deprecations; устаревшая `gemini-2.0-flash` отключена). Модель вынесена в `BuildConfig.GEMINI_MODEL`, что позволяет легко изменять её через `local.properties`.
- **Thinking Config**: Если поддерживается моделью/API, задается минимальный thinking (`thinkingConfig = {"thinkingBudget": 0}` или эквивалент) для минимизации задержки (latency) и стоимости (cost).
- **OkHttp Timeouts**: 30 секунд (connect, read, write).
- **GenerationConfig**:
  - `temperature`: 0.2
  - `responseMimeType`: `"application/json"` (для режима WORD)
  - `responseSchema`: JSON-схема технического задания.

### 3.3. Режимы запроса (WORD vs TEXT)
- **WORD mode**:
  - Входная строка содержит 1–3 токена и не содержит знаков конца предложения (`.`, `!`, `?`, `;`, `\n`).
  - Охватывает отдельные слова, глаголы с отделяемыми приставками, устойчивые сочетания ("sich freuen auf").
  - Возвращает структурированный JSON согласно схеме.
- **TEXT mode**:
  - Текст от 4 токенов или содержащий знаки препинания / переносы строк.
  - Обычный связный перевод без словарной разметки.
  - Сохраняет абзацы и форматирование. Ограничение 5000 символов со счетчиком символов.
- **Определение направления**:
  - Наличие кириллических символов (`\p{IsCyrillic}`) -> `RU → DE`.
  - Латиница (включая ä, ö, ü, ß) -> `DE → RU`.
  - Кнопка смены направления позволяет вручную зафиксировать пару. По умолчанию поддерживается только пара DE ⇄ RU.
- **Контекстное поле**:
  - Поле «Контекст (предложение)» передается в запрос. Модель выбирает значение, наиболее подходящее к контексту, и помечает его флагом `best_match = true`.

### 3.4. DTO и схема для режима WORD
```kotlin
@Serializable
data class WordTranslationDto(
    val lemma: String,
    val article: String? = null,
    val pos: String,
    val plural: String? = null,
    val genitive: String? = null,
    val verb_forms: VerbFormsDto? = null,
    val government: String? = null,
    val comparative: String? = null,
    val superlative: String? = null,
    val meanings: List<MeaningDto>,
    val synonyms: List<String> = emptyList(),
    val common_collocations: List<CollocationDto> = emptyList()
)

@Serializable
data class VerbFormsDto(
    val praesens_er: String,
    val praeteritum: String,
    val partizip2: String,
    val auxiliary: String,
    val separable: Boolean,
    val prefix: String? = null
)

@Serializable
data class MeaningDto(
    val translation: String,
    val note: String? = null,
    val register: String? = null,
    val example_de: String,
    val example_ru: String,
    val best_match: Boolean = false
)

@Serializable
data class CollocationDto(
    val de: String,
    val ru: String
)
```
- Все грамматические поля для нерелевантных частей речи объявлены nullable со значениями по умолчанию `null`/`emptyList()`, что исключает сбои десериализации.
- При сбое парсинга ответа выполняется **ровно один автоматический повторный запрос** (retry once).

### 3.5. Обработка ошибок с русскими сообщениями и кнопкой «Повторить»
1. Отсутствие сети / таймаут: *«Нет подключения к интернету. Проверьте сеть и повторите попытку.»*
2. Превышение квоты (HTTP 429): *«Превышен лимит запросов к переводчику. Подождите немного и повторите.»*
3. Ошибка сервера (HTTP 5xx): *«Сервис перевода временно недоступен. Попробуйте снова через минуту.»*
4. Блокировка контента (Safety Block): *«Запрос не может быть обработан из-за ограничений безопасности содержимого.»*
5. Пустой или некорректный ответ: *«Не удалось обработать ответ словаря. Попробуйте повторить запрос.»*

---

## 4. Алгоритм интервального повторения (SM-2)

### 4.1. Параметры состояния карточки
- `easeFactor` ($EF$): начальное значение 2.5; минимум 1.3; максимум 3.0.
- `repetitions` ($n$): счетчик последовательных успешных повторений.
- `intervalDays` ($I$): текущий интервал в календарных днях.
- `dueAt`: timestamp даты следующего повторения.

### 4.2. Маппинг 4 кнопок интерфейса
> **Правило вычисления повторений**: Условия на счетчик повторений `(n == 0, n == 1)` должны строго использовать значение $n$ **ДО** его инкрементации. В кодовой базе обязательно наличие unit-тестов с конкретными числовыми последовательностями интервалов для всех четырех оценок.

- **«Снова» (Again)**: Качество $q = 1$ (полный сброс):
  - $n' = 0$
  - $I' = 1$ день
  - $EF' = \max(1.3, EF - 0.20)$
- **«Трудно» (Hard)**: Качество $q = 3$ (успешно с трудом):
  - $I' = \max(1, (I \times 1.2).\text{roundToInt()})$
  - $n' = n + 1$
  - $EF' = \max(1.3, EF - 0.15)$
- **«Хорошо» (Good)**: Качество $q = 4$ (нормальный шаг):
  - Если $n == 0$ (до инкремента) $\Rightarrow I' = 1$
  - Если $n == 1$ (до инкремента) $\Rightarrow I' = 6$
  - Если $n \ge 2$ (до инкремента) $\Rightarrow I' = (I \times EF).\text{roundToInt()}$
  - $n' = n + 1$
  - $EF' = EF$ (согласно формуле SM-2: $EF + (0.1 - (5 - 4) \times (0.08 + (5 - 4) \times 0.02)) = EF$)
- **«Легко» (Easy)**: Качество $q = 5$ (легкий ответ с бонусом):
  - Если $n == 0$ (до инкремента) $\Rightarrow I' = 2$
  - Если $n == 1$ (до инкремента) $\Rightarrow I' = 7$
  - Если $n \ge 2$ (до инкремента) $\Rightarrow I' = (I \times EF \times 1.3).\text{roundToInt()}$
  - $n' = n + 1$
  - $EF' = \min(3.0, EF + 0.15)$

### 4.3. Очередь повторения
- Выборка карточек, у которых `dueAt <= now`.
- Приоритезация слабых слов: вторичная сортировка по `timesTranslated DESC` (слова, которые пользователь переводил многократно, повторяются первыми) и `easeFactor ASC`.
- Опция **«Повторить слова последнего урока»**: выбирает все слова из последней созданной сессии независимо от `dueAt`.
- Пустое состояние: сообщение «Все слова повторены! Отличная работа.»

### 4.4. Переключение направления (DE ⇄ RU)
- **Режим DE → RU**:
  - Лицевая сторона: немецкая лемма с артиклем жирным + иконка озвучки.
  - Обратная сторона: выбранное (или первое) русское значение + контекстный пример.
- **Режим RU → DE**:
  - Лицевая сторона: русский перевод.
  - Обратная сторона: немецкая лемма с артиклем + иконка озвучки + контекстный пример.

---

## 5. UI и Строгий минимализм (Strict Minimalism)

- **Цветовая схема**:
  - Светлая: Фон `#FAFAFA`, Поверхности `#FFFFFF`, Текст `#121212`, Вторичный `#757575`, Акцент `#3B5BDB`, Разделитель `#E0E0E0` (1dp).
  - Темная: Фон `#0E0E0E`, Поверхности `#161616`, Текст `#EDEDED`, Вторичный `#8C8C8C`, Акцент `#5C7CFA`, Разделитель `#262626` (1dp).
  - Никаких градиентов, теней и карточных elevation. Разграничение элементов только отступами и тонкой линией 1dp.
- **Типографика**: Inter (шрифт из ресурсов приложения / Google Fonts). Лемма 28sp Medium, основной текст 16sp Regular, вторичный 13sp.
- **Интерактивность и анимации**:
  - Touch targets не менее 48dp.
  - Анимация переворота карточки $\le 250$ мс с использованием `graphicsLayer(rotationY = ...)`.
  - Поддержка масштабирования шрифтов системы до 200%.
  - Все пользовательские строки вынесены в `res/values/strings.xml`.

---

## 6. Системная интеграция Android

1. **`ACTION_PROCESS_TEXT`**:
   - Фильтр интентов в `AndroidManifest.xml` для активности `MainActivity`:
     ```xml
     <intent-filter>
         <action android:name="android.intent.action.PROCESS_TEXT" />
         <category android:name="android.intent.category.DEFAULT" />
         <data android:mimeType="text/plain" />
     </intent-filter>
     ```
   - Заголовок действия: «DeutschLern – перевести».
   - При открытии: извлечение текста из `Intent.EXTRA_PROCESS_TEXT`, предзаполнение поля на вкладке «Перевод» и автоматический запуск перевода.
2. **`ACTION_SEND`**:
   - Фильтр интентов для `android.intent.action.SEND` с MIME-типом `text/plain`.
3. **Экспорт в CSV для Anki**:
   - Генерация файла по стандарту RFC 4180 с экранированием кавычек и запятых.
   - Формат колонок: `Front` («{article} {lemma}»), `Back` («{translation}<br>{exampleDe} — {exampleRu}»).
   - Передача через `FileProvider` и стандартный системный диалог `Intent.ACTION_SEND`.
4. **Android TextToSpeech**:
   - Локаль `Locale.GERMANY`.
   - Проверка наличия данных через `tts.isLanguageAvailable(Locale.GERMANY)`.
   - Если голос не установлен, отображается информационный `Snackbar` с действием «Установить», открывающим `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`.

---

## 7. Пофазный план реализации (Phases 0–6)

### Фаза 0: Планирование и согласование (Текущая фаза)
- Разработка детального архитектурного плана, валидация JSON-схем и алгоритмов.
- Фиксация допущений и открытых вопросов.
- **Остановка и ожидание подтверждения пользователя**.

### Фаза 1: Скелет проекта (Skeleton)
- Создание Android Gradle проекта с version catalog (`gradle/libs.versions.toml`).
- Настройка `minSdk 26`, latest stable `targetSdk/compileSdk` (API 35), Kotlin 2.0.21, Hilt (KSP), Compose BOM.
- Реализация темы строгого минимализма и навигации (3 вкладки: «Перевод», «Слова», «Повторение»).
- Проверка успешной сборки `./gradlew assembleDebug`.

### Фаза 2: Gemini в режиме TEXT
- Реализация `GeminiApiService`, OkHttp-клиента с 30с таймаутами и авторизационным интерцептором `x-goog-api-key`.
- Интеграция актуальной GA Flash-модели `gemini-3.5-flash` с минимальным thinking budget (для экономии задержки и квоты).
- Room кэш `CacheEntryEntity` с ключом: model + promptVersion + normalizedText (trim, collapse whitespace, Unicode NFC).
- Экран «Перевод» в режиме TEXT: ввод текста (до 5000 символов, счетчик), автоопределение направления и кнопка ручной смены, копирование/шаринг, русские сообщения об ошибках с кнопкой «Повторить».
- Модульные тесты на распознавание языка и генерацию ключей кэша.

### Фаза 3: Gemini в режиме WORD и карточка слова
- Системный промпт лексикографа в `Prompts.kt` и DTO модели с поддержкой однократного retry при сбое парсинга.
- UI словарной карточки: лемма, артикль, грамматический блок (множественное число, формы глаголов, управление), список значений с пометами и B2-примерами.
- Выбор значения («мое значение»), подсветка `best_match` при наличии контекста, сворачиваемые списки синонимов и коллокаций.
- Интеграция `TextToSpeech` с локалью `Locale.GERMANY` и Snackbar с действием «Установить» (`TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`).
- Модульные тесты парсинга JSON, восстановления после ошибок и определения режима WORD/TEXT.

### Фаза 4: Персистентность и вкладка «Слова»
- Сущности `WordEntryEntity`, `SessionEntity`, `SessionWordCrossRef` (таблица `session_words`), `TextTranslationEntity`.
- Автосохранение выполняется только для направления `DE -> RU`.
- Для `RU -> DE`: кнопка «Сохранить» для каждого немецкого эквивалента (сохраняется немецкая запись: lemma = немецкое слово, translation = русский запрос; русское слово никогда не сохраняется как lemma).
- Нормализация lemma (trim, Unicode NFC) перед проверкой `UNIQUE(lemma, pos)`.
- Таблица связей `session_words(sessionId, wordId, translatedAt)` с `UNIQUE(sessionId, wordId)`: слово попадает во все сессии переводов, прогресс SM-2 не перезаписывается.
- Вкладка «Слова»: фильтры («Все» / «Новые» / «Учу» / «Выучено»), поиск без учета регистра и умлаутов, группировка по сессиям, Bottom Sheet с карточкой слова, свайп для удаления с отменой (Undo Snackbar), сортировки.
- In-memory Room тесты для проверки логики upsert, сессий и фильтрации.

### Фаза 5: Вкладка «Повторение» (Flashcards & SM-2)
- Модуль `Sm2Scheduler` и сущность `ReviewStateEntity`.
- Логика условий `(n == 0, n == 1)` строго на значении ДО инкрементации.
- Unit-тесты с конкретными числовыми последовательностями шагов для всех 4 оценок («Снова», «Трудно», «Хорошо», «Легко»).
- Очередь повторения на сегодня с приоритизацией слабых слов.
- Карточка со сменой стороны (анимация переворота $\le 250$ мс), 4 кнопки оценки.
- Переключатель направления DE ⇄ RU, режим «Повторить слова последнего урока».

### Фаза 6: Интеграция, полировка и тесты
- Регистрация фильтров `ACTION_PROCESS_TEXT` и `ACTION_SEND`.
- Экспорт в CSV для Anki через системный Share Sheet.
- Аудит доступности (touch targets $\ge 48$dp, масштабирование шрифта) и проверка контраста темной темы.
- Правила R8 / ProGuard для сохранения DTO-моделей.
- Документация `README.md` (настройка API-ключа, архитектура, смена модели).
- Запуск полного набора unit-тестов.

---

## 8. Согласованные решения (Approved Architecture Decisions)

1. **Модель Gemini Flash**: Используется актуальная модель `gemini-3.5-flash` с поддержкой `BuildConfig.GEMINI_MODEL` и `local.properties`. Задается минимальный thinking budget.
2. **Сессии и уроки**: Реализована таблица связей `session_words(sessionId, wordId, translatedAt)` с составным ключом `UNIQUE(sessionId, wordId)`. Поле `words.sessionId` не перезаписывается, SM-2 прогресс сохраняется.
3. **Сохранение направлений**: Автосохранение активно только для `DE -> RU`. Для `RU -> DE` отображается кнопка «Сохранить» у каждого немецкого варианта. Лемма всегда на немецком языке.
4. **Нормализация леммы и кэша**: Лемма нормализуется (trim, NFC) перед проверкой уникальности. Ключ кэша строится с учетом модели, версии промпта и NFC-нормализованного текста.
5. **TTS интеграция**: При отсутствии немецкого голосового пакета показывается `Snackbar` с кнопкой «Установить» -> `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`.
6. **Алгоритм SM-2**: Условия `(n == 0, n == 1)` вычисляются ДО инкремента. Числовые тесты покрывают все 4 оценки.
7. **Версии SDK**: `minSdk 26`, `compileSdk/targetSdk` настроены на latest stable (API 35).
