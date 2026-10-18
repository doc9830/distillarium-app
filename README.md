# distillarium-app

Android-приложение «Дистиллярий»: вики, справочник, калькуляторы, партии и
рецепты. Офлайн-первый: весь контент лежит на устройстве, сеть нужна только
для обновления контента.

**Лицензия:** source available, см. [`LICENSE`](LICENSE). Код открытым
исходным не является.

---

## Статус

| Этап | Состояние |
| --- | --- |
| M0. Каркас и конвейер контента | собран и проверен на JVM и на реальном устройстве; CI готов, но не запускался на GitHub |
| M1. Вики | основной сценарий собран и проверен на устройстве: дерево, статья, вкладки, ссылки, обратные ссылки, поиск по синонимам; FTS5 не вводили — 32 статьи индексируются в памяти |
| M2. Справочник и калькуляторы | не начат |
| M3. Партии и рецепты | не начат |
| M4. Остальные калькуляторы | не начат |
| M5. Полировка и релиз | не начат |

Контент живёт в отдельном репозитории
[`distillarium-content`](../distillarium-content): правка статьи не требует
новой сборки APK.

## Структура

```
app/                      Android-приложение: Compose, «Знания» и «Обновления»
  src/main/java/.../knowledge/  разбор статей, дерево, поиск, загрузка пакета
  src/main/java/.../ui/         экраны, Markdown-рендер, навигация
  src/main/assets/        content-pack.zip и manifest.json (встроенный пакет)
core/content/             Kotlin/JVM: манифест, проверка, ZIP, подмена, откат
core/data/                место для Room/FTS5; появится в M2–M3
tools/sync-content.mjs    копирование собранного пакета в assets
.github/workflows/app.yml тесты, lint, APK
```

`core/content` не знает об Android и UI: его тесты идут на JVM без эмулятора.
`app` — тонкая обёртка: assets, OkHttp, WorkManager, Compose. `core/data`
намеренно пуст: в M1 ещё нет партий и `user.db`, а создавать Room-заглушку
«на будущее» значит тащить зависимость без задачи.

## Стек

| Слой | Решение |
| --- | --- |
| Язык и UI | Kotlin 2.2.20 + Jetpack Compose, Material 3 |
| Markdown | CommonMark 0.30.1 + GFM-таблицы, свой Compose-рендер |
| Поиск M1 | индекс в памяти, синонимы и лёгкое усечение словоформ |
| Обновления | WorkManager + OkHttp |
| JSON | kotlinx.serialization |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Gradle / AGP | 8.13 / 8.13.0 |

Версии зафиксированы в `gradle/libs.versions.toml`. Compose BOM и AndroidX
взяты из стабильной ветки, современной Kotlin 2.2.20: обновлять их нужно
вместе с Kotlin, а не по одному. Lint предупреждает о новых версиях — это
ожидаемо и не ошибка сборки.

## Команды

Нужны JDK 21 и Android SDK с platform 36 / build-tools 36.0.0.

```bash
export JAVA_HOME="$HOME/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Android/Sdk"

./gradlew :core:content:test :app:testDebugUnitTest   # конвейер и встроенный пакет
./gradlew lintDebug             # Android Lint
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

Полная локальная проверка:

```bash
./gradlew --no-daemon :core:content:test :app:testDebugUnitTest lintDebug assembleDebug
```

Проверено на реальном устройстве **2409FPCC4G, HyperOS, Android 16 (API 36)**:

- debug APK устанавливается и запускается (холодный старт ≈1.5–2 с);
- первый запуск ставит встроенный пакет: `source=embedded`, 32 статьи;
- после перезапуска `installedAt` не меняется, повторной установки нет;
- `databases/` пуст — `user.db` не создаётся;
- WorkManager ставит периодическую работу на 24 ч с `UNMETERED`;
- фоновая проверка при недоступном сервере не показывает ошибку на экране.

## Контент в APK

Пакет лежит в `app/src/main/assets/`. Он собран из `distillarium-content`
и скопирован туда скриптом:

```bash
cd ../distillarium-content
node tools/build-content.mjs

cd ../distillarium-app
node tools/sync-content.mjs          # копирует и проверяет размеры/sha256
node tools/sync-content.mjs --check  # только проверка, без копирования
```

`EmbeddedContentTest` открывает именно эти assets, устанавливает пакет в
temp-каталог и проверяет 32 статьи, 29 ссылок, 33 редиректа и 34 файла.
Если контент и валидатор приложения разойдутся, тест упадёт до релиза.

## Вики M1

- `WikiLoader` читает активный пакет из `files/content/current`, а не из APK:
  после обновления статьи сразу новые.
- `ArticleParser` разбирает frontmatter без YAML-библиотеки: строки, массивы,
  кавычки и многострочный `short` из конвейера контента.
- `ArticleBodyParser` вынимает `::: tabs` / `== Название ==`; остальное тело
  остаётся Markdown.
- `WikiTree` строит дерево по id (`raw/...`, `processes/...`) и считает статьи
  в ветках. Теги M1 выводятся из `tags`, категории и корня, потому что во
  встроенном пакете поле `tags` пока пустое.
- `WikiLibrary.resolve` понимает id и старые адреса `/articles/...` из
  `redirects.json`; `backlinks` собираются по `[[ссылкам]]` при загрузке.
- `SearchEngine` ищет по заголовку, подзаголовкам, короткому описанию и тексту,
  понимает «сивушные» → хвосты и «сахаромер» → ареометр.
- `DistillariumRoot` держит нижнюю навигацию `Знания | Обновления` и стек
  статей. Читалка: крошки, оглавление в шторке, A−/A+, вкладки оборудования,
  таблицы с горизонтальным скроллом, «Сюда ссылаются», «Наверх», «Поделиться».
- `EXTRA_ARTICLE_ID` / `EXTRA_ARTICLE_HEADING` в `MainActivity` позволяют
  открыть статью и раздел по intent — используется для проверки на устройстве.

## Адрес обновлений

По умолчанию:

```
https://github.com/distillarium/distillarium-content/releases/download/content-latest/manifest.json
```

Владелец репозитория на GitHub ещё не подтверждён. Адрес можно переопределить
без правки кода:

```bash
./gradlew -Pdistillarium.contentManifestUrl=https://example.org/content/manifest.json assembleDebug
```

или строкой в `~/.gradle/gradle.properties`. Разрешён только HTTPS. Архив
берётся из соседнего asset: `…/content-pack.zip` рядом с `manifest.json`.
Релиз `content-latest` должен содержать оба файла — так его собирает
workflow репозитория контента.

## Что происходит при запуске и обновлении

1. `MainActivity` создаёт `DistillariumRoot`; `KnowledgeViewModel` просит
   `ContentRepository.currentDirectory()`, а `UpdatesViewModel` —
   `ensureInstalled()`.
2. Если `files/content/current` цел — используется он. Если нет — previous,
   затем встроенный пакет. Сеть для этого не нужна. Вики читается из
   `current`, поэтому после обновления не нужен перезапуск.
3. Обновление: GET манифеста с `If-None-Match` → сравнение версий как чисел →
   проверка `schemaVersion` и `minAppVersion` → скачивание в
   `files/content/download.tmp` → проверка размера и sha256 всего архива →
   распаковка в `update.tmp` с защитой от zip-slip и лимитом объёма →
   проверка каждого файла, ссылок, редиректов, медиа и индекса поиска →
   `current → previous`, `update.tmp → current`, запись `installed.json`.
4. Любая ошибка до подмены оставляет `current` нетронутым. После подмены
   сбой записи состояния возвращает previous на место.
5. Откат: `current → broken.tmp`, `previous → current`; бывший current
   становится previous, поэтому откат обратим.

Раскладка на устройстве:

```
files/
  content/
    current/            активный пакет (только чтение)
    previous/           один предыдущий пакет
    update.tmp/         временная распаковка, удаляется
    broken.tmp/         только на время отката
    previous.json       состояние previous для отката
    etag.txt            ETag манифеста (не пользовательские данные)
    download.tmp        временный архив, удаляется
  installed.json        {contentVersion, schemaVersion, installedAt, packSha256, source}
databases/
  user.db               НЕ ТРОГАЕТСЯ конвейером контента
```

`source` — расширение формата (`embedded`, `download`, `rollback`,
`recovered`), нужно только экрану «Обновления».

## Гарантии и тесты

1. Обновление не меняет `user.db`: тест создаёт файл в `databases/`,
   запоминает sha256, ставит новый пакет и сверяет хеш.
2. Битый архив, неверный sha256, zip-slip, битая ссылка, слишком новая схема
   и слишком новый `minAppVersion` не устанавливаются; `current` остаётся.
3. Откат возвращает previous, а прежний current оставляет доступным.
4. Обрыв процесса после `current → previous` и посреди отката
   восстанавливается при следующем запуске (`previous` или `broken.tmp`).
5. Офлайн: после первой установки `ensureInstalled()` не требует assets и
   сети; тест передаёт заведомо отсутствующие файлы.

Тесты: `core/content/src/test` (установщик, загрузчик, версии) и
`app/src/test` (реальный встроенный пакет).

## Ограничения M1

- Нет партий, калькуляторов и полноценных настроек. Нижняя навигация пока
  `Знания | Обновления`; «Обновления» позже переедут в настройки.
- Поиск работает по индексу в памяти. `search/index.json` проверяется
  конвейером, но в SQLite/FTS5 не переносится; Room подключим, когда появятся
  пользовательские данные.
- Переключатель `Статьи | Таблицы` и справочник — M2.
- Markdown-картинки пока показываются заглушкой: медиа в пакете ещё нет.
- Таблицы статьи скроллятся горизонтально; фиксированная первая колонка —
  вместе со справочником в M2.
- WorkManager проверяет обновления раз в сутки по unmetered-сети; уведомлений
  об обновлении пока нет (M5).
- `core/data` и Room не подключены: пользовательских таблиц ещё нет.
- Проверено на реальном устройстве HyperOS/Android 16; эмулятор не
  использовался. Автоматические нажатия по UI не выполнялись: HyperOS
  ограничивает `adb shell input` без «Отладки по USB (безопасность)».
  Статьи и разделы открывались через intent-extra, экраны сверены по скриншотам.
- Лицензия на контент ещё не выбрана (см. `docs/10` в корне проекта).
- Git remote и запуск GitHub Actions не настроены.

## Что нельзя нарушать

1. Обновление контента пишет **только** в `files/content/` и
   `files/installed.json`. В `databases/user.db` — никогда.
2. Любая ошибка скачивания, распаковки или проверки оставляет текущий пакет
   нетронутым. Приложение продолжает работать офлайн.
3. Контент не выполняет код: только Markdown и JSON.
4. Пакет новее `schemaVersion` приложения не ставится.
5. `minAppVersion` больше версии приложения — пакет не ставится, показывается
   предложение обновить приложение.
