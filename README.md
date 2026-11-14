# AntiSpam Shield: Блокировка спама и защита от прослушки

> **Полное название:** AntiSpam Shield: Блокировка спама и защита от прослушки  
> **Короткое название:** AntiSpam Shield  
> **Суть продукта:** Суверенный персональный файрвол связи и анти-шпионский щит смартфона с философией **Нулевой Телеметрии (Zero-Telemetry Telecom & Privacy Firewall)**.  
> В отличие от коммерческих определителей номеров, мы **НИКОГДА** не выгружаем вашу телефонную книгу в облако. Все 10 000 000+ спам-номеров проверяются прямо внутри процессора вашего смартфона за 1 мс без доступа к сети.

---

### 🛡️ Манифест Нулевой Телеметрии (Zero-Telemetry Manifesto)
1. **Никакой выгрузки контактов (Zero Cloud Upload):** Телефонная книга и журнал звонков не покидают устройство.
2. **Локальная обработка на процессоре (On-Device Local-First):** Алгоритм $O(\log N)$ в Room SQLite с временем отклика $< 1.5$ мс. Работает без интернета.
3. **Криптография Ed25519 & Anti-Rollback:** Математическая гарантия неизменности базы. Защита от отравления базы и блокировок экстренных номеров (112, скорая).
4. **Анти-шпионский щит:** Защита микрофона от прослушки (MicGuard), аудит скрытой переадресации (`*#21#`), защита от 2G/IMSI перехватчиков и авто-очистка трекеров в ссылках.
5. **GMS-Free & Офлайн P2P:** Полная независимость от Google Play Services. Передача проверенной базы по Bluetooth / Quick Share без интернета.

---

Проект состоит из двух взаимодействующих модулей:
1. **Серверный бэкенд (Python / FastAPI / SQLite-WAL / SQLAlchemy):** 
   - Сборщик реестра Минцифры (453k диапазонов ABC/DEF) и B2B/SIP классификатор.
   - Движок двухуровневой кластеризации с защитой от **Sybil-атак** (Device Trust Score, Subnet Rate Limiting `/24`).
   - Защита от коллизий **MNP** в DEF-кодах (точечные блокировки `SINGLE_NUMBER` при $< 6$ номеров) и механизм **Авто-амнистии** (`POST /api/v1/report/false-positive`).
   - **In-Memory WriteBuffer** (`asyncio.Queue`) для пакетной записи жалоб без блокировок SQLite.
   - Server Whitelist с управлением группами (Т-Банк, Сбербанк, 112) с защитой `X-Admin-Api-Key`.
   - **Velocity Detector**: Распознавание всплесков предиктивного автодозвона ($\ge 2$ жалоб от разных подсетей за $\le 15$ мин) со снижением порога веса до 1.8 и ускоренной эскалацией сотен DEF при 3 номерах.
   - **Binary Compact Protocol (`/api/v1/pools/binary`)**: Бинарный протокол дельт ASPB (64-битные диапазоны) с компрессией 6–8x по сравнению с JSON и поддержкой HTTP 304.
   - **Apple iOS CallKit Export (`/api/v1/export/ios`)**: Экспорт строго упорядоченных последовательностей номеров (`CXCallDirectoryPhoneNumber`) в TXT/JSON и CLI-утилита `export-ios`.
   - Фоновый TTL-воркер (30/60 дней) и REST API синхронизации с поддержкой **HTTP 304 Not Modified** (0 КБ).

2. **Мобильный клиент (Android / Kotlin / Gradle / Room / WorkManager / Retrofit):**
   - Системное ядро `CallScreeningService` (Android 10+, API 29–35) со строгим $O(\log N)$ поиском в Room (задержка **< 0.1 мс**).
   - Защита от подмены номеров (**Caller ID Spoofing**) через `callerNumberVerificationStatus` (ЕПВВ «Антифрод»).
   - Режим **«Жду курьера / доставку»** (временная пауза сброса на 2 часа с переводом звонков в тихий режим `setSilenceCall(true)`).
   - Контур **Call Privacy Shield** (защита голосовой линии от фонового прослушивания мессенджерами и супераппами):
     - `MicGuardService` (мониторинг `AudioManager.AudioRecordingCallback` в реальном времени при звонке).
     - `MicJammerManager` (монопольный аудиофокус `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` с заполнением буфера шпиона нулями).
     - `SpeakerphoneMonitor` (детекция утечки звука через спикерфон).
     - `PrivacyScannerHelper` (аудит установленных приложений на связку `READ_PHONE_STATE` + `RECORD_AUDIO` с 1-клик отзывом прав).
     - `PrivateCallLogVaultManager` (режим Инкогнито: автоматическое скрытие звонков из системного `CallLog.Calls` с переносом в защищенный сейф Room).
   - Локальный аудит-лог заблокированных вызовов (`BlockedCallRecordEntity`) с объяснением причин блокировки.
   - **Оппортунистическая синхронизация** при звонке (обход агрессивного засыпания WorkManager на MIUI/OneUI).
   - Модуль **Post-Call UX** (порог $< 30$ сек, авто-скрытие уведомления через 60 сек, умный фильтр недавних исходящих вызовов).
   - Apple HIG минималистичный интерфейс (Jetpack Compose Material 3).

---

## Архитектура системы

```
                              ┌───────────────────────────────────┐
                              │    Сервер (FastAPI / SQLite-WAL)  │
                              │ - ETL реестра Минцифры (ABC/DEF) │
                              │ - Движок кластеризации (100/1000) │
                              │ - Server Whitelist (Банки/112)    │
                              │ - TTL Worker (30/60 дней)         │
                              │ - Поддержка HTTP 304 (0 КБ)       │
                              └───────────────▲───────────────────┘
                                              │ REST API
                                              │ (Deltas + Batch Reports)
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Android Клиент (Kotlin / Jetpack Compose)               │
│                                                                             │
│  ┌───────────────────────────────┐        ┌──────────────────────────────┐  │
│  │ AntiSpamCallScreeningService  │        │   SyncPoolsWorker            │  │
│  │ (Перехват вызова < 15 мс)     │        │   (Каждые 6 ч + BatteryNotLow)│  │
│  │  1. In-Memory LRU Contacts    │        │  - Сброс очереди жалоб       │  │
│  │  2. Room: local_whitelist     │        │  - Запрос GET /api/v1/pools  │  │
│  │  3. Room: blocked_pools (mmap)│        │  - HTTP 304: 0 КБ трафика    │  │
│  │  -> CallResponse (REJECT)     │        └──────────────▲───────────────┘  │
│  └───────────────▲───────────────┘                       │                  │
│                  │                                       │                  │
│                  └───────────────────┬───────────────────┘                  │
│                                      ▼                                      │
│                  ┌──────────────────────────────────────┐                   │
│                  │   Локальная база данных Room SQLite  │                   │
│                  │ (PRAGMA mmap_size = 64MB, seed.db)   │                   │
│                  └──────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Структура репозитория

```
d:\Antispam\
  android\                             # Нативный Android-проект (Gradle KTS)
    app\
      src\main\
        java\ru\antispam\app\
          data\local\                  # Room: Database, DAO, Entity, PRAGMA mmap
          data\remote\                 # Retrofit, OkHttp, DTO, HTTP 304
          postcall\                    # PhoneStateReceiver (< 30s), NotificationHelper
          receiver\                    # BootCompletedReceiver (предпрогрев базы)
          service\                     # AntiSpamCallScreeningService (SLA < 15 мс)
          ui\                          # Jetpack Compose UI в стиле Apple HIG
            calllog\                   # Журнал звонков и Hero Shield виджет
            components\                # AppleGroupedCard, AppleSegmentedControl, Sheet
            settings\                  # Настройки фильтрации и синхронизации
            theme\                     # Apple палитра (Canvas, OLED Black, Mint, Blue)
            whitelist\                 # Управление Whitelist с тумблерами для банков
          util\                        # PhoneNormalizer, ContactLookupHelper (LRU)
          worker\                      # SyncPoolsWorker (WorkManager, BatteryNotLow)
          AntiSpamApplication.kt
          MainActivity.kt
        assets\
          antispam_registry_seed.db    # Предзагруженная база для оффлайн-старта
        AndroidManifest.xml            # ROLE_CALL_SCREENING, BootReceiver, Permissions
      src\test\                        # Unit-тесты и Benchmark Suite (10,000 вызовов)
      build.gradle.kts
      proguard-rules.pro
    gradle\libs.versions.toml          # Gradle Version Catalog
    build.gradle.kts
    settings.gradle.kts
    gradlew / gradlew.bat
  backend\                             # Серверный модуль (FastAPI / ETL / CLI)
    api\                               # Роуты, Pydantic схемы, Lifespan, HTTP 304
    clustering.py                      # Двухуровневая кластеризация
    whitelist.py                       # Server Whitelist (TBANK, SBER, 112)
    ttl_worker.py                      # Фоновый воркер очистки просроченных блоков
    downloader.py / parser.py          # ETL реестра Минцифры
    models.py / database.py            # SQLAlchemy и SQLite-WAL
    cli.py                             # CLI управления
  tests\                               # 25 автоматических тестов бэкенда + бенчмарк
  requirements.txt
```

---

## Запуск и тестирование

### 1. Серверный бэкенд
```bash
# Установка зависимостей
pip install -r requirements.txt

# Запуск тестов бэкенда (32 теста)
pytest tests/ -v

# Запуск бенчмарка производительности и SLA (< 15 мс)
python tests/benchmark_backend.py

# Синхронизация реестра Минцифры
python -m backend.cli sync

# Экспорт номеров для Apple iOS CallKit Extension
python -m backend.cli export-ios --output CallDirectoryNumbers.txt --limit 50000

# Запуск REST API сервера (локально)
python -m backend.cli server --host 127.0.0.1 --port 8000

# Запуск сбора базы ВЦИОМ
python -m backend.cli seed-vciom
```

### 2. Производственный деплой (Docker & Docker Compose)

> [!IMPORTANT]
> **Критическое правило запуска Uvicorn:** Сервер всегда запускается со строгим флагом `--workers 1`!
> Поскольку внутри `lifespan` FastAPI работают in-memory `WriteBuffer` (на базе `asyncio.Queue`) и SQLite WAL, запуск нескольких воркеров разделит очереди и приведет к гонкам версий и блокировкам базы. Один асинхронный воркер легко обслуживает $> 150\text{ req/s}$ ($\approx 13\text{ млн}$ запросов в сутки).

```bash
# Запуск производственного контейнера с постоянным томом antispam_data
docker compose up -d

# Проверка статуса и healthcheck
docker compose ps
docker compose logs -f
```

### 3. Настройка HTTPS и Reverse Proxy (Nginx / Caddy)
Готовые конфигурации для проксирования и автоматического получения SSL-сертификатов Let's Encrypt находятся в каталоге `deploy/`:
- `deploy/nginx.conf`: Nginx с SSL, HTTP->HTTPS редиректом, сжатием и пробросом заголовков `X-Real-IP` и `X-Forwarded-For`.
- `deploy/Caddyfile`: Ультра-легкий обратный прокси с автоматическим получением сертификатов.

### 4. Android клиент
Проект в папке `android/` готов к открытию в **Android Studio** или сборке через консоль:
- В `network_security_config.xml` по умолчанию запрещен незашифрованный `cleartext HTTP` трафик (защита реальных устройств в сетях операторов связи).
- Для локальной разработки и отладки разрешены эмуляторы (`10.0.2.2`, `localhost`).
```bash
cd android
./gradlew testDebugUnitTest
```
