package ru.antispam.app.privacy.model

/**
 * Category of Russian service that requires split tunneling / VPN masking.
 */
enum class RussianAppCategory(val title: String) {
    BANKING("Банки и Финансы"),
    GOVERNMENT("Госуслуги и Налоги"),
    SUPERAPP_AND_TELECOM("Супераппы и Операторы"),
    MARKETPLACE_AND_MEDIA("Маркетплейсы и ТВ")
}

/**
 * Entry in the registry of Russian services that block VPN or leak DPI telemetry.
 */
data class TargetServiceEntry(
    val packageName: String,
    val defaultName: String,
    val category: RussianAppCategory,
    val reason: String
)

/**
 * Info about an installed target app on the user's device.
 */
data class MaskingTargetApp(
    val packageName: String,
    val appName: String,
    val category: RussianAppCategory,
    val isInstalled: Boolean
)

/**
 * Registry of known Russian applications, banks, and government services that:
 * 1. Block access when `hasTransport(TRANSPORT_VPN)` is TRUE.
 * 2. Throttle traffic or demand disabling VPN.
 * 3. Harvest VPN endpoint IPs for DPI filtering.
 */
object RussianAppRegistry {

    val TARGET_SERVICES: List<TargetServiceEntry> = listOf(
        // --- 1. Банки и Финансы ---
        TargetServiceEntry(
            "ru.sberbankmobile",
            "Сбербанк Онлайн",
            RussianAppCategory.BANKING,
            "Блокирует переводы при включенном VPN, проверяет TRANSPORT_VPN"
        ),
        TargetServiceEntry(
            "ru.sberbank.sberinvestor",
            "СберИнвестор",
            RussianAppCategory.BANKING,
            "Требует прямого соединения для биржевых котировок"
        ),
        TargetServiceEntry(
            "ru.tcsbank.mbank",
            "Т-Банк (Тинькофф)",
            RussianAppCategory.BANKING,
            "Проверяет интерфейс tun0, ограничивает операции из-за рубежа"
        ),
        TargetServiceEntry(
            "com.idamob.tinkoff.invest",
            "Т-Инвестиции",
            RussianAppCategory.BANKING,
            "Блокирует терминал при обнаружении зарубежного IP"
        ),
        TargetServiceEntry(
            "ru.vtb24.mobilebanking",
            "ВТБ Онлайн",
            RussianAppCategory.BANKING,
            "Жесткий запрет доступа при активном VPN"
        ),
        TargetServiceEntry(
            "ru.alfabank.mobile.android",
            "Альфа-Банк",
            RussianAppCategory.BANKING,
            "Детектирует VPN-маршрутизацию и выдает ошибку входа"
        ),
        TargetServiceEntry(
            "ru.gazprombank.android.mobilebank.app",
            "Газпромбанк",
            RussianAppCategory.BANKING,
            "Блокирует авторизацию через иностранные IP-адреса"
        ),
        TargetServiceEntry(
            "ru.raiffeisennews",
            "Райффайзенбанк",
            RussianAppCategory.BANKING,
            "Ограничивает доступ к счетам при иностранном соединении"
        ),
        TargetServiceEntry(
            "ru.sovcomcard.halva.v1",
            "Совкомбанк (Халва)",
            RussianAppCategory.BANKING,
            "Сбоит при подключении через туннель"
        ),
        TargetServiceEntry(
            "ru.ozon.fintech.bank",
            "Озон Банк",
            RussianAppCategory.BANKING,
            "Блокирует переводы по СБП при включенном VPN"
        ),
        TargetServiceEntry(
            "ru.yandex.bank",
            "Яндекс Банк",
            RussianAppCategory.BANKING,
            "Требует прямого физического интерфейса для работы"
        ),
        TargetServiceEntry(
            "ru.psbank.mobile.retail",
            "ПСБ",
            RussianAppCategory.BANKING,
            "Полный запрет иностранной и VPN-маршрутизации"
        ),
        TargetServiceEntry(
            "ru.mts.money",
            "МТС Банк",
            RussianAppCategory.BANKING,
            "Ограничивает платежные сервисы"
        ),
        TargetServiceEntry(
            "ru.pochtabank.mobile",
            "Почта Банк",
            RussianAppCategory.BANKING,
            "Блокирует операции с картами"
        ),
        TargetServiceEntry(
            "ru.rshb.dbo",
            "Россельхозбанк",
            RussianAppCategory.BANKING,
            "Запрет подключения через прокси и VPN"
        ),
        TargetServiceEntry(
            "ru.nspk.mirpay",
            "Mir Pay",
            RussianAppCategory.BANKING,
            "Отказывает в токенизации карт при обнаружении VPN"
        ),
        TargetServiceEntry(
            "ru.nspk.sbp",
            "СБПэй",
            RussianAppCategory.BANKING,
            "Блокирует быстрые платежи"
        ),

        // --- 2. Госуслуги и Налоги ---
        TargetServiceEntry(
            "com.gosuslugi",
            "Госуслуги",
            RussianAppCategory.GOVERNMENT,
            "Блокирует доступ по GeoIP при зарубежном выходе VPN"
        ),
        TargetServiceEntry(
            "ru.gosuslugi2",
            "Госуслуги Авто / Культура",
            RussianAppCategory.GOVERNMENT,
            "Геоблокировка нероссийских IP-адресов"
        ),
        TargetServiceEntry(
            "ru.nalog.fl",
            "Налоги ФЛ (ФНС)",
            RussianAppCategory.GOVERNMENT,
            "Не открывает личный кабинет налогоплательщика"
        ),
        TargetServiceEntry(
            "ru.fns.billchecker",
            "Проверка чеков ФНС",
            RussianAppCategory.GOVERNMENT,
            "Таймаут соединения через иностранные маршруты"
        ),
        TargetServiceEntry(
            "ru.mos.app",
            "Госуслуги Москвы (Мос.ру)",
            RussianAppCategory.GOVERNMENT,
            "Блокирует передачу показаний счетчиков и доступ к дневнику"
        ),
        TargetServiceEntry(
            "ru.pfrf.mobile",
            "Социальный фонд (ПФР)",
            RussianAppCategory.GOVERNMENT,
            "Гео-фильтрация зарубежных пулов IP"
        ),

        // --- 3. Супераппы, Соцсети и Операторы ---
        TargetServiceEntry(
            "ru.vk.max",
            "Мессенджер MAX",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Детектирует VPN, сканирует инвентарь приложений"
        ),
        TargetServiceEntry(
            "com.vkontakte.android",
            "ВКонтакте",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Телеметрия собирает данные о сетевых интерфейсах"
        ),
        TargetServiceEntry(
            "ru.vk.store",
            "RuStore",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Медленная загрузка обновлений через зарубежные узлы"
        ),
        TargetServiceEntry(
            "ru.rostel",
            "Ростелеком",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Блокирует вход в кабинет абонента"
        ),
        TargetServiceEntry(
            "ru.mts.mymts",
            "Мой МТС",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Сбоит при проверке баланса через VPN"
        ),
        TargetServiceEntry(
            "ru.megafon.mlk",
            "МегаФон",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Ошибка авторизации по номеру через VPN"
        ),
        TargetServiceEntry(
            "ru.beeline.services",
            "билайн",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Требует прямого мобильного подключения"
        ),
        TargetServiceEntry(
            "ru.tele2.mytele2",
            "T2 (Tele2)",
            RussianAppCategory.SUPERAPP_AND_TELECOM,
            "Ошибка входа в кабинет абонента"
        ),

        // --- 4. Маркетплейсы, Карты и Медиа ---
        TargetServiceEntry(
            "com.wildberries",
            "Wildberries",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Высокие задержки и капчи при зарубежном IP"
        ),
        TargetServiceEntry(
            "ru.wildberries.work",
            "WB Партнеры",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Блокировка кабинета поставщика"
        ),
        TargetServiceEntry(
            "ru.ozon.app.android",
            "Ozon",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Постоянные капчи Cloudflare при нероссийском IP"
        ),
        TargetServiceEntry(
            "com.avito.android",
            "Авито",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Блокирует профиль при входе с иностранного IP"
        ),
        TargetServiceEntry(
            "ru.yandex.taxi",
            "Яндекс Go",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Сбоит геопозиционирование при активном VPN"
        ),
        TargetServiceEntry(
            "ru.yandex.yandexbus",
            "Яндекс Карты / Навигатор",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Некорректная загрузка дорожной обстановки"
        ),
        TargetServiceEntry(
            "com.rutube.app",
            "Rutube",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Отказывает в воспроизведении видео через VPN"
        ),
        TargetServiceEntry(
            "ru.kinopoisk",
            "Кинопоиск",
            RussianAppCategory.MARKETPLACE_AND_MEDIA,
            "Лицензионные ограничения воспроизведения за пределами РФ"
        )
    )

    /**
     * Set of package names in the registry for fast lookups.
     */
    val ALL_PACKAGE_NAMES: Set<String> by lazy {
        TARGET_SERVICES.map { it.packageName }.toSet()
    }
}
