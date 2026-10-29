package com.lumaos.pro.launcher.icons

import com.lumaos.pro.launcher.core.model.ComponentKey
import java.util.Locale
import kotlin.math.max

/** Category grouping matching `assets/living_icons/manifest.json`. */
enum class LivingIconCategory(val storageKey: String) {
    CORE("core"),
    POPULAR("popular")
}

/**
 * Canonical catalog of the 30 bundled LumaOS Pro Living Icons.
 *
 * Each asset is a 1024x1024 sRGB RGBA PNG with transparent margins outside its
 * optical-glass silhouette. [visibleLeft], [visibleTop], [visibleRight] and
 * [visibleBottom] record the measured silhouette bounds on the 1024x1024 canvas
 * so [IconRenderer] aligns every Living Icon to the exact same visual footprint
 * as standard icons without clipping corners or adding flat background plates.
 */
enum class LivingIconId(
    val storageKey: String,
    val displayName: String,
    val category: LivingIconCategory,
    val filename: String,
    val sha256: String,
    val visibleLeft: Int,
    val visibleTop: Int,
    val visibleRight: Int,
    val visibleBottom: Int
) {
    PHONE("phone", "Phone", LivingIconCategory.CORE, "phone.png", "5ebbb0861d1916161f9680e108076e849d56bd2da6213246b4987710f53d350d", 104, 101, 919, 923),
    MESSAGES("messages", "Messages", LivingIconCategory.CORE, "messages.png", "30aeb177d295952f9b4e4470b6f57cc77c51724ee27ecc970306d54370f4a786", 104, 101, 919, 923),
    CAMERA("camera", "Camera", LivingIconCategory.CORE, "camera.png", "ccd983a4192418dbad8d507a5a86b64a1b7c7c5462f41572e4a0efd0bbfcc6e5", 104, 101, 920, 923),
    PHOTOS("photos", "Photos / Gallery", LivingIconCategory.CORE, "photos.png", "be1e12415ba64f7625579ecd3927419e66ac2a84516bb72a1272526317cf7caa", 104, 101, 920, 923),
    BROWSER("browser", "Browser", LivingIconCategory.CORE, "browser.png", "88ff3c5f46436b7b9837e9692fc1adb56b86aed61430d0ee77de0971565a4d8d", 101, 101, 922, 923),
    SETTINGS("settings", "Settings", LivingIconCategory.CORE, "settings.png", "8b2d271cf4f75ef2a066dea1d80f7c349892e7975e3d89a30d02df6828fcdbbb", 101, 102, 923, 922),
    MUSIC("music", "Music", LivingIconCategory.CORE, "music.png", "be3b05dec0babc89900e3e3dcefcff341b502ce406a1686eaeefe8617cb591bb", 104, 101, 920, 923),
    CALENDAR("calendar", "Calendar", LivingIconCategory.CORE, "calendar.png", "9442887ab3106ea6d15208ef649ceeb30acc35aef599180d3a248abf656bbd9b", 104, 101, 920, 923),
    CLOCK("clock", "Clock", LivingIconCategory.CORE, "clock.png", "fc1f2be2d9aa0f7b8c4d46d537e8450b3bfe95b21a8c3b1db22e04fc1d30773e", 101, 102, 923, 921),
    FILES("files", "Files", LivingIconCategory.CORE, "files.png", "b687ab2edc5b32108df6472a2ee6979ce47637b1607c3cd21d9f330cab50561d", 104, 101, 920, 923),
    CALCULATOR("calculator", "Calculator", LivingIconCategory.CORE, "calculator.png", "1744cd1dda6862fab6f727e8e3963b073421f2289eb3ccf777317a5723e7607b", 104, 101, 920, 923),
    CONTACTS("contacts", "Contacts", LivingIconCategory.CORE, "contacts.png", "df9c81b43a336ebe7f0a82e06ba8048aa2d43f313c5887acaf4d996e2f6b1f4c", 104, 101, 919, 923),
    MAPS("maps", "Maps", LivingIconCategory.CORE, "maps.png", "740775de11eaea357a28018fe9a007d6def49eeb87af84361942f3113aafe878", 104, 101, 919, 923),
    WEATHER("weather", "Weather", LivingIconCategory.CORE, "weather.png", "f59058745206653841fcbdb046e8fb38e96049ef16717e013ab2eab5e9277197", 104, 101, 920, 923),
    RECORDER("recorder", "Recorder", LivingIconCategory.CORE, "recorder.png", "ebe00d558930efddcb9c9798281e5fcce64eceedfe906bf31ab0db6b03f765f8", 104, 101, 920, 923),
    NOTES("notes", "Notes", LivingIconCategory.CORE, "notes.png", "9dfd7b56d1d59ff7b29aad8670e868927ffd3e31b0cfc9ad7af685505e951d07", 104, 101, 920, 923),
    MAIL("mail", "Mail", LivingIconCategory.CORE, "mail.png", "c4b1dee67a14f3b4afa72aff8eeb16bbe504bc11797dad4fa6f10696b2085a70", 104, 101, 920, 923),
    DOWNLOADS("downloads", "Downloads", LivingIconCategory.CORE, "downloads.png", "8108fbd4ae1217d920b4b8f37d69a78d76a31c70b9bf454d9ce48907aefa126c", 104, 101, 920, 923),
    APP_STORE("app_store", "App Store / Play Store", LivingIconCategory.CORE, "app_store.png", "227d65a035fb9ed4c2bbcf0e7e9d4e2af083f03d117c60d7ff0f7eb56b85c718", 104, 101, 920, 923),
    BATTERY("battery", "Battery", LivingIconCategory.CORE, "battery.png", "b596c7e3456a55f61750f564ed31c09361909d38b1b1077ec70bdfa08c56d73a", 104, 101, 920, 923),
    WHATSAPP("whatsapp", "WhatsApp", LivingIconCategory.POPULAR, "whatsapp.png", "c31478d61c07e56b4977cfc9291731a43863a40fb4af18906945633d461c5de4", 108, 101, 915, 923),
    INSTAGRAM("instagram", "Instagram", LivingIconCategory.POPULAR, "instagram.png", "9d981fe12c0e4b16d2d065fb41a1f31eb4993ea3a990d28855bd85ace7d41be7", 104, 101, 919, 923),
    YOUTUBE("youtube", "YouTube", LivingIconCategory.POPULAR, "youtube.png", "069f65bcebcb9154c949cc506b3a665f27236db4625efdba5bbfd90151a50ed5", 104, 101, 919, 923),
    SPOTIFY("spotify", "Spotify", LivingIconCategory.POPULAR, "spotify.png", "cc1b97e8e908c3b3629691a3748ab996fabe438ded4fe0bf6698237fbecc2df3", 104, 101, 920, 923),
    TELEGRAM("telegram", "Telegram", LivingIconCategory.POPULAR, "telegram.png", "ef82927ac0a68c6e8341b953161871aeb2ca05eb507317db580c8ea4db254849", 104, 101, 920, 923),
    DISCORD("discord", "Discord", LivingIconCategory.POPULAR, "discord.png", "305ba9109ef1cd99f739bbd72870573cf459168bf0f52bfeb8799e8ef248ed3b", 104, 101, 920, 923),
    GMAIL("gmail", "Gmail", LivingIconCategory.POPULAR, "gmail.png", "f892110986e9cbcd1ac83d672ab128fe29b6157cac16dbe9c42d7ba890e1bc2e", 101, 102, 923, 922),
    GOOGLE_DRIVE("google_drive", "Google Drive", LivingIconCategory.POPULAR, "google_drive.png", "4a1409d91261e616a713edf6c3ea0b2a80f31f6d3bb7081f1632ea77b81a4dce", 104, 101, 920, 923),
    GOOGLE_CHROME("google_chrome", "Google Chrome", LivingIconCategory.POPULAR, "google_chrome.png", "6a569e58a0b5bd4f75a4cd213a5e0cb3c552381271e717bb566e52e0c2fab114", 101, 106, 923, 918),
    GOOGLE_PHOTOS("google_photos", "Google Photos", LivingIconCategory.POPULAR, "google_photos.png", "8f39b6c5ea28ac86bc2d2ee6b12d38291317c53f20bfb28ea1a6bfe43664e0df", 104, 101, 919, 923);

    val assetPath: String get() = "$ASSET_DIRECTORY/$filename"

    /** Longest visible silhouette dimension normalized to [0..1] of the 1024px canvas. */
    val visibleSpanFraction: Float
        get() = max(visibleRight - visibleLeft, visibleBottom - visibleTop) / MASTER_CANVAS_PX

    /** Optical horizontal center normalized to [0..1] of the 1024px canvas. */
    val centerXFraction: Float
        get() = ((visibleLeft + visibleRight) * 0.5f) / MASTER_CANVAS_PX

    /** Optical vertical center normalized to [0..1] of the 1024px canvas. */
    val centerYFraction: Float
        get() = ((visibleTop + visibleBottom) * 0.5f) / MASTER_CANVAS_PX

    companion object {
        const val ASSET_DIRECTORY = "living_icons"
        const val MANIFEST_ASSET_PATH = "$ASSET_DIRECTORY/manifest.json"
        const val MASTER_CANVAS_PX = 1024f

        private val byStorageKey: Map<String, LivingIconId> =
            entries.associateBy { it.storageKey }

        fun fromStorageKey(raw: String?): LivingIconId? =
            raw?.trim()?.lowercase(Locale.ROOT)?.let(byStorageKey::get)

        fun byCategory(category: LivingIconCategory): List<LivingIconId> =
            entries.filter { it.category == category }
    }
}

/** Per-app user customization over the default verified Living Icon mapping. */
sealed interface LivingIconOverride {
    /** Always use the installed application's own native Android icon. */
    data object UseSystem : LivingIconOverride

    /** Explicitly assign a specific bundled [LivingIconId] to this app component. */
    data class UseLiving(val icon: LivingIconId) : LivingIconOverride

    val storageValue: String
        get() = when (this) {
            UseSystem -> SYSTEM_TOKEN
            is UseLiving -> icon.storageKey
        }

    companion object {
        const val SYSTEM_TOKEN = "system"

        fun fromStorageValue(raw: String?): LivingIconOverride? {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (cleaned == SYSTEM_TOKEN) return UseSystem
            return LivingIconId.fromStorageKey(cleaned)?.let(::UseLiving)
        }
    }
}

/**
 * Deterministic, upgrade-safe string codec for per-component icon overrides.
 *
 * Format: `0:com.pkg/com.pkg.Main=phone|0:com.other/com.other.Main=system`
 * Malformed entries or unrecognized future tokens are skipped rather than
 * failing preferences deserialization.
 */
object LivingIconOverridesCodec {

    fun encode(overrides: Map<ComponentKey, LivingIconOverride>): String {
        if (overrides.isEmpty()) return ""
        return overrides.entries
            .sortedBy { it.key.serialize() }
            .joinToString("|") { (key, value) -> "${key.serialize()}=${value.storageValue}" }
    }

    fun decode(raw: String?): Map<ComponentKey, LivingIconOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<ComponentKey, LivingIconOverride>()
        for (token in raw.split('|')) {
            val eq = token.lastIndexOf('=')
            if (eq <= 0 || eq >= token.lastIndex) continue
            val key = ComponentKey.parse(token.substring(0, eq).trim()) ?: continue
            val override = LivingIconOverride.fromStorageValue(token.substring(eq + 1)) ?: continue
            result[key] = override
        }
        return result
    }
}

/**
 * Verified package and activity-component resolver for LumaOS Pro Living Icons.
 *
 * Design invariants:
 *  - Uses only verified Android application IDs and explicit component rules for
 *    multi-launcher system packages (e.g., combined Contacts/Dialer or Files/Downloads).
 *  - Never guesses from localized display labels, so unrelated apps never receive
 *    a random or mismatched Living Icon.
 *  - Keeps generic `photos`, `browser` and `mail` distinct from `google_photos`,
 *    `google_chrome` and `gmail`.
 */
object LivingIconResolver {

    private val CONTACTS_DIALER_SHARED_PACKAGES = setOf(
        "com.android.contacts",
        "com.samsung.android.contacts"
    )

    private val DOCUMENTS_UI_PACKAGES = setOf(
        "com.android.documentsui",
        "com.google.android.documentsui"
    )

    /**
     * Explicit verified package-to-asset table covering:
     *  - the 10 popular apps in `manifest.json`
     *  - the 20 core system/utility apps across AOSP, Google/Pixel, Samsung One UI,
     *    Xiaomi/MIUI, OnePlus/ColorOS, LineageOS, Motorola, Nothing, Sony, and ASUS.
     */
    val VERIFIED_PACKAGE_MAP: Map<String, LivingIconId> = mapOf(
        // --- Popular third-party & Google branded apps (10) ---
        "com.whatsapp" to LivingIconId.WHATSAPP,
        "com.instagram.android" to LivingIconId.INSTAGRAM,
        "com.google.android.youtube" to LivingIconId.YOUTUBE,
        "com.spotify.music" to LivingIconId.SPOTIFY,
        "org.telegram.messenger" to LivingIconId.TELEGRAM,
        "org.telegram.messenger.web" to LivingIconId.TELEGRAM,
        "com.discord" to LivingIconId.DISCORD,
        "com.google.android.gm" to LivingIconId.GMAIL,
        "com.google.android.gm.lite" to LivingIconId.GMAIL,
        "com.google.android.apps.docs" to LivingIconId.GOOGLE_DRIVE,
        "com.android.chrome" to LivingIconId.GOOGLE_CHROME,
        "com.google.android.apps.photos" to LivingIconId.GOOGLE_PHOTOS,

        // --- Core: Phone ---
        "com.google.android.dialer" to LivingIconId.PHONE,
        "com.android.dialer" to LivingIconId.PHONE,
        "com.samsung.android.dialer" to LivingIconId.PHONE,
        "com.oneplus.dialer" to LivingIconId.PHONE,
        "com.asus.dialer" to LivingIconId.PHONE,

        // --- Core: Messages ---
        "com.google.android.apps.messaging" to LivingIconId.MESSAGES,
        "com.android.messaging" to LivingIconId.MESSAGES,
        "com.android.mms" to LivingIconId.MESSAGES,
        "com.samsung.android.messaging" to LivingIconId.MESSAGES,
        "com.oneplus.mms" to LivingIconId.MESSAGES,

        // --- Core: Camera ---
        "com.google.android.GoogleCamera" to LivingIconId.CAMERA,
        "com.android.camera" to LivingIconId.CAMERA,
        "com.android.camera2" to LivingIconId.CAMERA,
        "org.lineageos.aperture" to LivingIconId.CAMERA,
        "org.lineageos.snap" to LivingIconId.CAMERA,
        "com.sec.android.app.camera" to LivingIconId.CAMERA,
        "com.oneplus.camera" to LivingIconId.CAMERA,
        "com.oplus.camera" to LivingIconId.CAMERA,
        "com.motorola.camera2" to LivingIconId.CAMERA,
        "com.motorola.camera3" to LivingIconId.CAMERA,
        "com.nothing.camera" to LivingIconId.CAMERA,
        "com.sonyericsson.android.camera" to LivingIconId.CAMERA,

        // --- Core: Photos / Gallery (distinct from Google Photos) ---
        "com.android.gallery3d" to LivingIconId.PHOTOS,
        "com.google.android.apps.photosgo" to LivingIconId.PHOTOS,
        "com.sec.android.gallery3d" to LivingIconId.PHOTOS,
        "com.miui.gallery" to LivingIconId.PHOTOS,
        "com.oneplus.gallery" to LivingIconId.PHOTOS,
        "com.coloros.gallery3d" to LivingIconId.PHOTOS,
        "org.lineageos.glimpse" to LivingIconId.PHOTOS,
        "com.motorola.MotGallery2" to LivingIconId.PHOTOS,
        "com.sonyericsson.album" to LivingIconId.PHOTOS,

        // --- Core: Browser (distinct from Google Chrome) ---
        "com.android.browser" to LivingIconId.BROWSER,
        "org.lineageos.jelly" to LivingIconId.BROWSER,
        "com.sec.android.app.sbrowser" to LivingIconId.BROWSER,
        "com.mi.globalbrowser" to LivingIconId.BROWSER,
        "com.heytap.browser" to LivingIconId.BROWSER,

        // --- Core: Settings ---
        "com.android.settings" to LivingIconId.SETTINGS,

        // --- Core: Music (distinct from Spotify) ---
        "com.android.music" to LivingIconId.MUSIC,
        "org.lineageos.eleven" to LivingIconId.MUSIC,
        "org.lineageos.twelve" to LivingIconId.MUSIC,
        "com.sec.android.app.music" to LivingIconId.MUSIC,
        "com.miui.player" to LivingIconId.MUSIC,
        "com.sonyericsson.music" to LivingIconId.MUSIC,

        // --- Core: Calendar ---
        "com.google.android.calendar" to LivingIconId.CALENDAR,
        "com.android.calendar" to LivingIconId.CALENDAR,
        "org.lineageos.etar" to LivingIconId.CALENDAR,
        "com.samsung.android.calendar" to LivingIconId.CALENDAR,
        "com.oneplus.calendar" to LivingIconId.CALENDAR,
        "com.coloros.calendar" to LivingIconId.CALENDAR,

        // --- Core: Clock ---
        "com.google.android.deskclock" to LivingIconId.CLOCK,
        "com.android.deskclock" to LivingIconId.CLOCK,
        "com.sec.android.app.clockpackage" to LivingIconId.CLOCK,
        "com.oneplus.deskclock" to LivingIconId.CLOCK,
        "com.coloros.alarmclock" to LivingIconId.CLOCK,

        // --- Core: Files ---
        "com.google.android.apps.nbu.files" to LivingIconId.FILES,
        "com.google.android.documentsui" to LivingIconId.FILES,
        "com.android.documentsui" to LivingIconId.FILES,
        "com.sec.android.app.myfiles" to LivingIconId.FILES,
        "com.mi.android.globalFileexplorer" to LivingIconId.FILES,
        "com.android.fileexplorer" to LivingIconId.FILES,
        "com.oneplus.filemanager" to LivingIconId.FILES,
        "com.coloros.filemanager" to LivingIconId.FILES,

        // --- Core: Calculator ---
        "com.google.android.calculator" to LivingIconId.CALCULATOR,
        "com.android.calculator2" to LivingIconId.CALCULATOR,
        "com.sec.android.app.popupcalculator" to LivingIconId.CALCULATOR,
        "com.miui.calculator" to LivingIconId.CALCULATOR,
        "com.oneplus.calculator" to LivingIconId.CALCULATOR,
        "com.coloros.calculator" to LivingIconId.CALCULATOR,

        // --- Core: Contacts ---
        "com.google.android.contacts" to LivingIconId.CONTACTS,
        "com.android.contacts" to LivingIconId.CONTACTS,
        "com.samsung.android.app.contacts" to LivingIconId.CONTACTS,
        "com.samsung.android.contacts" to LivingIconId.CONTACTS,
        "com.oneplus.contacts" to LivingIconId.CONTACTS,

        // --- Core: Maps ---
        "com.google.android.apps.maps" to LivingIconId.MAPS,
        "com.google.android.apps.mapslite" to LivingIconId.MAPS,

        // --- Core: Weather ---
        "com.google.android.apps.weather" to LivingIconId.WEATHER,
        "com.sec.android.daemonapp" to LivingIconId.WEATHER,
        "com.miui.weather2" to LivingIconId.WEATHER,
        "net.oneplus.weather" to LivingIconId.WEATHER,
        "com.coloros.weather2" to LivingIconId.WEATHER,
        "com.nothing.weather" to LivingIconId.WEATHER,

        // --- Core: Recorder ---
        "com.google.android.apps.recorder" to LivingIconId.RECORDER,
        "com.android.soundrecorder" to LivingIconId.RECORDER,
        "org.lineageos.recorder" to LivingIconId.RECORDER,
        "com.sec.android.app.voicenote" to LivingIconId.RECORDER,
        "com.oneplus.soundrecorder" to LivingIconId.RECORDER,
        "com.coloros.soundrecorder" to LivingIconId.RECORDER,
        "com.nothing.soundrecorder" to LivingIconId.RECORDER,

        // --- Core: Notes ---
        "com.google.android.keep" to LivingIconId.NOTES,
        "com.samsung.android.app.notes" to LivingIconId.NOTES,
        "com.miui.notes" to LivingIconId.NOTES,
        "com.oneplus.note" to LivingIconId.NOTES,
        "com.coloros.note" to LivingIconId.NOTES,

        // --- Core: Mail (distinct from Gmail) ---
        "com.android.email" to LivingIconId.MAIL,
        "com.google.android.email" to LivingIconId.MAIL,
        "com.samsung.android.email.provider" to LivingIconId.MAIL,

        // --- Core: Downloads ---
        "com.android.providers.downloads.ui" to LivingIconId.DOWNLOADS,

        // --- Core: App Store / Play Store ---
        "com.android.vending" to LivingIconId.APP_STORE,

        // --- Core: Battery ---
        "com.miui.powerkeeper" to LivingIconId.BATTERY,
        "com.coloros.oppoguardelf" to LivingIconId.BATTERY
    )

    /**
     * Resolves the [LivingIconId] for [key], honoring per-component [overrides]
     * first and [livingIconsEnabled] second. Returns `null` when the app should
     * render its native Android icon (or user-selected external icon pack).
     */
    fun resolve(
        key: ComponentKey,
        livingIconsEnabled: Boolean = true,
        overrides: Map<ComponentKey, LivingIconOverride> = emptyMap()
    ): LivingIconId? {
        // Check exact profile key first, then user-0 template key so personal
        // overrides can also cover mirrored work-profile components unless overridden.
        val exactOverride = overrides[key]
            ?: if (key.userSerial != 0L) overrides[key.copy(userSerial = 0L)] else null
        when (exactOverride) {
            LivingIconOverride.UseSystem -> return null
            is LivingIconOverride.UseLiving -> return exactOverride.icon
            null -> {}
        }
        if (!livingIconsEnabled) return null
        return resolveDefault(key.packageName, key.className)
    }

    /** Resolves the verified default Living Icon for a component without user overrides. */
    fun resolveDefault(packageName: String, className: String = ""): LivingIconId? {
        if (packageName.isBlank()) return null
        val simpleClass = className.substringAfterLast('.').lowercase(Locale.ROOT)

        if (packageName in CONTACTS_DIALER_SHARED_PACKAGES && simpleClass.isNotEmpty()) {
            if ("dialtacts" in simpleClass || "dialer" in simpleClass) {
                return LivingIconId.PHONE
            }
            return LivingIconId.CONTACTS
        }

        if (packageName in DOCUMENTS_UI_PACKAGES && simpleClass.isNotEmpty()) {
            if ("download" in simpleClass) {
                return LivingIconId.DOWNLOADS
            }
            return LivingIconId.FILES
        }

        if (packageName == "com.android.settings" && simpleClass.isNotEmpty()) {
            if ("powerusage" in simpleClass || "battery" in simpleClass || "fuelgauge" in simpleClass) {
                return LivingIconId.BATTERY
            }
            return LivingIconId.SETTINGS
        }

        return VERIFIED_PACKAGE_MAP[packageName]
    }

    /** Package-level lookup for surfaces such as WidgetPicker app headers. */
    fun resolvePackage(
        packageName: String,
        livingIconsEnabled: Boolean = true
    ): LivingIconId? {
        if (!livingIconsEnabled || packageName.isBlank()) return null
        return VERIFIED_PACKAGE_MAP[packageName]
    }
}
