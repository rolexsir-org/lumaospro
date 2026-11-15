package com.lumaos.pro.launcher.context

/**
 * In-app disclosure consent for the optional accessibility grant behind Focus
 * Protection. Pure Kotlin: no Android imports, so every rule below is unit
 * tested on the JVM.
 *
 * Why this exists as separate state:
 *
 * Android's accessibility permission and the user's agreement to LumaOS Pro's
 * disclosure are two different facts. The permission can be granted from the
 * system screen without LumaOS Pro ever having explained the feature, it can
 * survive an app update that changes the disclosure text, and it can be
 * revoked at any moment. Folding the two into one "is it on" flag (which is
 * what the pre-1.5.0 code effectively did) makes it impossible to answer
 * "did the user actually agree to this?" honestly.
 *
 * So the record below is the consent fact, stored in DataStore, and
 * [FocusAccess.isGranted] remains the permission fact, read from
 * `Settings.Secure`. Enforcement requires *both*:
 *
 *  - [mayEnforce] states the combined rule; the service — which is only called
 *    while the framework holds the grant — applies [grantsAccess], and every
 *    status surface derives from [uiState];
 *  - a granted permission with no recorded consent (for example, the user
 *    enabled the service directly in system settings) never enforces;
 *  - a recorded consent with the permission revoked simply means "awaiting
 *    permission", which is a supported steady state, not an error.
 *
 * [CURRENT_DISCLOSURE_VERSION] is bumped only when the disclosure's meaning
 * changes materially. An older recorded version stops satisfying the gate
 * until the user answers the current text again, so consent cannot silently
 * outlive the wording it was given for. Declining is a real answer at the
 * current version: it is remembered, it is respected, and the app never
 * re-prompts by itself.
 */
enum class FocusAccessConsentState {
    /** The user has never answered the in-app disclosure. */
    NOT_ASKED,

    /** The user pressed Agree on the current disclosure text. */
    GRANTED,

    /** The user pressed Decline, or withdrew an earlier agreement. */
    DECLINED
}

/**
 * The persisted consent fact. [decidedAtMillis] is diagnostic only (shown as
 * "Agreed on …") and never used to expire consent; [disclosureVersion] ties
 * the answer to the exact wording the user saw.
 */
data class FocusAccessConsentRecord(
    val state: FocusAccessConsentState = FocusAccessConsentState.NOT_ASKED,
    val disclosureVersion: Int = 0,
    val decidedAtMillis: Long = 0L
)

/**
 * How the accessibility row and the disclosure screen should present the
 * combination of consent and permission. A pure derivation, so the UI cannot
 * invent a status the stored facts do not support.
 */
enum class FocusAccessUiState {
    /** No answer to the current disclosure yet: show the disclosure first. */
    NEEDS_DISCLOSURE,

    /** The user declined: layer 2 stays off and is not pushed again. */
    DECLINED,

    /** Consented, but Android does not currently hold the grant. */
    AWAITING_PERMISSION,

    /** Consented and granted: layer 2 is running. */
    ACTIVE
}

object FocusAccessConsent {

    /**
     * Bump when the disclosure text changes what the user is agreeing to
     * (a new event type, a new field read, new storage, new transmission).
     * Cosmetic edits do not need a bump.
     */
    const val CURRENT_DISCLOSURE_VERSION: Int = 1

    val NOT_ASKED: FocusAccessConsentRecord = FocusAccessConsentRecord()

    /** Tolerant parse of the stored state name; anything unknown is "not asked". */
    fun parseState(raw: String?): FocusAccessConsentState =
        FocusAccessConsentState.entries.firstOrNull { it.name == raw } ?: FocusAccessConsentState.NOT_ASKED

    /** An explicit agreement to the current disclosure text. */
    fun agree(nowMillis: Long): FocusAccessConsentRecord = FocusAccessConsentRecord(
        state = FocusAccessConsentState.GRANTED,
        disclosureVersion = CURRENT_DISCLOSURE_VERSION,
        decidedAtMillis = nowMillis
    )

    /** An explicit refusal (or a withdrawal of earlier consent). */
    fun decline(nowMillis: Long): FocusAccessConsentRecord = FocusAccessConsentRecord(
        state = FocusAccessConsentState.DECLINED,
        disclosureVersion = CURRENT_DISCLOSURE_VERSION,
        decidedAtMillis = nowMillis
    )

    /**
     * True until the user has answered the *current* disclosure text. A
     * decline at the current version counts as answered; this only means
     * "the user has not seen and answered this version", never "prompt now".
     */
    fun requiresDisclosure(record: FocusAccessConsentRecord): Boolean =
        record.state == FocusAccessConsentState.NOT_ASKED ||
            record.disclosureVersion < CURRENT_DISCLOSURE_VERSION

    /** True only for an agreement to the current disclosure text. */
    fun grantsAccess(record: FocusAccessConsentRecord): Boolean =
        record.state == FocusAccessConsentState.GRANTED &&
            record.disclosureVersion >= CURRENT_DISCLOSURE_VERSION

    /**
     * The one enforcement gate: layer 2 may act only when the user agreed to
     * the current disclosure *and* Android currently holds the grant.
     */
    fun mayEnforce(record: FocusAccessConsentRecord, permissionGranted: Boolean): Boolean =
        permissionGranted && grantsAccess(record)

    /** Status derivation shared by the Settings row and the disclosure screen. */
    fun uiState(record: FocusAccessConsentRecord, permissionGranted: Boolean): FocusAccessUiState = when {
        grantsAccess(record) && permissionGranted -> FocusAccessUiState.ACTIVE
        grantsAccess(record) -> FocusAccessUiState.AWAITING_PERMISSION
        record.state == FocusAccessConsentState.DECLINED &&
            record.disclosureVersion >= CURRENT_DISCLOSURE_VERSION -> FocusAccessUiState.DECLINED
        else -> FocusAccessUiState.NEEDS_DISCLOSURE
    }
}

/**
 * Narrow persistence port for consent, so the decision flow is testable with
 * an in-memory fake. The production implementation is backed by the existing
 * DataStore preferences ([com.lumaos.pro.launcher.core.prefs.LumaPrefs]).
 */
interface FocusAccessConsentStore {
    suspend fun readConsent(): FocusAccessConsentRecord
    suspend fun writeConsent(record: FocusAccessConsentRecord)
}

/**
 * The disclosure's decision flow. The activity only calls these three
 * functions and then navigates; the ordering guarantee that matters for Play
 * policy lives here — the consent record is written *before* [agree] returns,
 * so the system accessibility screen is never opened ahead of a stored
 * agreement even if the process dies immediately afterwards.
 *
 * [clock] is injectable so tests can assert the recorded timestamp without
 * sleeping.
 */
class FocusAccessConsentFlow(
    private val store: FocusAccessConsentStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun read(): FocusAccessConsentRecord = store.readConsent()

    /** Persists an agreement to the current text, then returns it. */
    suspend fun agree(): FocusAccessConsentRecord =
        FocusAccessConsent.agree(clock()).also { store.writeConsent(it) }

    /** Persists a refusal. No permission screen is ever opened after this. */
    suspend fun decline(): FocusAccessConsentRecord =
        FocusAccessConsent.decline(clock()).also { store.writeConsent(it) }

    /**
     * Withdraws an earlier agreement. Identical stored state to [decline], a
     * separate entry point because the screens say different things: Android's
     * grant may still be on until the user turns it off in system settings, and
     * this record alone already stops enforcement.
     */
    suspend fun withdraw(): FocusAccessConsentRecord =
        FocusAccessConsent.decline(clock()).also { store.writeConsent(it) }
}
