package io.github.a1mohamad.toeflvocab.core.localization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

// MARK: - Language

@Serializable
enum class AppLanguage {
    @SerialName("system")
    System,

    @SerialName("english")
    English,

    @SerialName("persian")
    Persian;

    val rawValue: String
        get() = when (this) {
            System -> "system"
            English -> "english"
            Persian -> "persian"
        }

    val id: String get() = rawValue

    /** Always shown in the language's own script, never translated. */
    val nativeName: String
        get() = when (this) {
            System -> "System"
            English -> "English"
            Persian -> "فارسی"
        }

    val isRightToLeft: Boolean get() = resolved == Persian

    /** [System] resolved against the device's preferred languages. */
    val resolved: AppLanguage
        get() {
            if (this != System) return this
            val preferred = Locale.getDefault().language
            return if (preferred.startsWith("fa")) Persian else English
        }

    companion object {
        val allCases: List<AppLanguage> = listOf(System, English, Persian)
    }
}

// MARK: - Keys

/**
 * Every piece of UI copy in the app.
 *
 * Deliberately a Kotlin table rather than `res/values-fa/strings.xml`:
 * localized resource folders are one of the easier things to misconfigure, and
 * a misconfiguration here would only show up as raw keys on screen after a full
 * CI round trip — there is no local emulator on this project. A plain map cannot
 * fail to build, is unit-testable (see `StringsTest`), and moves to Android
 * resources later without touching a single call site.
 *
 * The second reason is behavioural: Android resource resolution follows the
 * *device* locale list, and this app lets the user pick a language independently
 * of the device. Overriding that per-process is possible but fiddly; a map keyed
 * on the user's own choice simply does what the setting says.
 */
enum class StringKey {
    // Common
    CommonCancel, CommonDone, CommonClose, CommonNext, CommonStart,
    CommonContinue, CommonBack, CommonReset, CommonQuit, CommonKeepGoing,

    // Tabs
    TabStudy, TabReports, TabSettings,

    // Library
    LibraryTitle, LibrarySubtitle, LibraryContinue, LibraryEmpty, LibraryEmptyHint,

    // Book
    BookSectionsCount, BookWordsCount, BookStart, BookAbout, BookProgress,

    // Section
    SectionChooseList, SectionBegin, SectionAbout,
    CategoryMain, CategoryExtra, CategoryMainSubtitle, CategoryExtraSubtitle,
    SectionNeedWork, SectionNotStarted, SectionComplete, SectionNoExtras,

    // Practice
    PracticeKnewIt, PracticeDidntKnow, PracticeMeaning, PracticeNextWord,
    PracticeFinish, PracticeProgress, PracticeLastFive, PracticeThisCycle,
    PracticeCycleComplete, PracticeQuitTitle, PracticeQuitMessage,
    PracticeTapToHear, PracticeNewWord, PracticeCorrectTally, PracticeWrongTally,
    PracticeSpeakLabel, PracticeChecklistLabel, PracticeTip,

    // Summary
    SummaryTitle, SummaryAccuracy, SummaryAnswered, SummaryNextSection,
    SummaryPracticeAgain, SummaryBackToMenu, SummaryBookComplete, SummaryHeadline,

    // Reports
    ReportsTitle, ReportsOverall, ReportsMastered, ReportsSeen, ReportsAccuracy,
    ReportsByBook, ReportsWeakest, ReportsRecent, ReportsExtraPractice,
    ReportsExtraSubtitle, ReportsStartDrill, ReportsEmpty, ReportsEmptyHint,
    ReportsMainVsExtra, ReportsScope, ReportsRun, ReportsWordsTotal, StatWords,
    ScopeWeakest25, ScopeWeakest50, ScopeEverything,

    // Restart / loop
    RestartTitle, RestartMessage, RestartAction, RestartLater,
    ExtraLoopTitle, ExtraLoopMessage,

    // Settings
    SettingsTitle, SettingsAppearance, SettingsTheme,
    ThemeSystem, ThemeLight, ThemeDark,
    SettingsLanguage, SettingsPronunciation, SettingsAccent,
    AccentAmerican, AccentBritish, AccentAustralian,
    SettingsSpeed, SpeedSlow, SpeedNormal, SpeedFast,
    SettingsAutoSpeak, SettingsAutoSpeakHint, SettingsHaptics,
    SettingsData, SettingsResetProgress, SettingsResetMessage,
    SettingsExportProgress, SettingsImportProgress, SettingsBackupHint,
    BackupRestoreTitle, BackupRestoreMessage, BackupRestoreAction,
    BackupRestoredTitle, BackupExportFailed, BackupImportFailed, CommonOK,
    SettingsPrivacy, SettingsPrivacyBody, SettingsAbout, SettingsVersion,
    AboutTitle, AboutBody, AboutContentTitle, AboutContentBody;

    /**
     * The lowerCamelCase spelling the iOS table uses. Kept because the test
     * "no key falls back to its own name" is about what a user would see on
     * screen, and because it keeps the two string tables diffable.
     */
    val rawValue: String get() = name.replaceFirstChar { it.lowercase() }

    companion object {
        val allCases: List<StringKey> = entries.toList()
    }
}

// MARK: - Table

/**
 * Resolves keys for the active language, falling back to English for any key a
 * translation is missing so a gap shows readable copy, never a raw key.
 */
class Strings(language: AppLanguage) {

    val language: AppLanguage = language.resolved

    operator fun get(key: StringKey): String {
        if (language == AppLanguage.Persian) {
            persian[key]?.let { return it }
        }
        return english[key] ?: key.rawValue
    }

    /** For keys whose value contains `%d` / `%s` placeholders. */
    fun format(key: StringKey, vararg arguments: Any): String =
        String.format(formattingLocale, this[key], *arguments)

    /**
     * Persian numerals come from the locale, not from the string table, so a
     * count formatted into Persian copy has to be formatted with a Persian
     * locale or "۲۵ واژه ضعیف" would sit next to a Latin "12".
     */
    private val formattingLocale: Locale
        get() = if (language == AppLanguage.Persian) Locale.forLanguageTag("fa") else Locale.US

    companion object {

        val english: Map<StringKey, String> = mapOf(
            StringKey.CommonCancel to "Cancel",
            StringKey.CommonDone to "Done",
            StringKey.CommonClose to "Close",
            StringKey.CommonNext to "Next",
            StringKey.CommonStart to "Start",
            StringKey.CommonContinue to "Continue",
            StringKey.CommonBack to "Back",
            StringKey.CommonReset to "Reset",
            StringKey.CommonQuit to "Quit",
            StringKey.CommonKeepGoing to "Keep going",

            StringKey.TabStudy to "Study",
            StringKey.TabReports to "Reports",
            StringKey.TabSettings to "Settings",

            StringKey.LibraryTitle to "Library",
            StringKey.LibrarySubtitle to "%d words across two books",
            StringKey.LibraryContinue to "Pick up where you left off",
            StringKey.LibraryEmpty to "No vocabulary found",
            StringKey.LibraryEmptyHint to "The bundled content files could not be read. This is a packaging problem, not something you did.",

            StringKey.BookSectionsCount to "%d sections",
            StringKey.BookWordsCount to "%d words",
            StringKey.BookStart to "Start studying",
            StringKey.BookAbout to "About this book",
            StringKey.BookProgress to "Progress",

            StringKey.SectionChooseList to "Choose a list",
            StringKey.SectionBegin to "Begin",
            StringKey.SectionAbout to "About this section",
            StringKey.CategoryMain to "Main",
            StringKey.CategoryExtra to "Extra",
            StringKey.CategoryMainSubtitle to "The book's own word list",
            StringKey.CategoryExtraSubtitle to "Extra words you collected",
            StringKey.SectionNeedWork to "%d need work",
            StringKey.SectionNotStarted to "Not started",
            StringKey.SectionComplete to "Complete",
            StringKey.SectionNoExtras to "No extra words in this section",

            StringKey.PracticeKnewIt to "I knew it",
            StringKey.PracticeDidntKnow to "Didn't know",
            StringKey.PracticeMeaning to "Meaning",
            StringKey.PracticeNextWord to "Next word",
            StringKey.PracticeFinish to "Finish",
            StringKey.PracticeProgress to "%d of %d",
            StringKey.PracticeLastFive to "Last 5",
            StringKey.PracticeThisCycle to "This cycle",
            StringKey.PracticeCycleComplete to "Cycle complete",
            StringKey.PracticeQuitTitle to "Quit this session?",
            StringKey.PracticeQuitMessage to "Everything you have answered so far is already saved.",
            StringKey.PracticeTapToHear to "Tap to hear it",
            StringKey.PracticeNewWord to "First time",
            StringKey.PracticeCorrectTally to "%d right",
            StringKey.PracticeWrongTally to "%d wrong",
            StringKey.PracticeSpeakLabel to "Pronounce %s",
            StringKey.PracticeChecklistLabel to "%d of %d answered, %d correct",
            StringKey.PracticeTip to "Tip",

            StringKey.SummaryTitle to "Section complete",
            StringKey.SummaryAccuracy to "Accuracy",
            StringKey.SummaryAnswered to "Answered",
            StringKey.SummaryNextSection to "Next section",
            StringKey.SummaryPracticeAgain to "Practise again",
            StringKey.SummaryBackToMenu to "Back to menu",
            StringKey.SummaryBookComplete to "That was the last section in this book.",
            StringKey.SummaryHeadline to "Nice work",

            StringKey.ReportsTitle to "Reports",
            StringKey.ReportsOverall to "Overall",
            StringKey.ReportsMastered to "Mastered",
            StringKey.ReportsSeen to "Seen",
            StringKey.ReportsAccuracy to "Accuracy",
            StringKey.ReportsByBook to "By book",
            StringKey.ReportsWeakest to "Needs the most work",
            StringKey.ReportsRecent to "Recent sessions",
            StringKey.ReportsExtraPractice to "Extra practice",
            StringKey.ReportsExtraSubtitle to "Drill your weakest words from anywhere in the library. Kept separate from main progress.",
            StringKey.ReportsStartDrill to "Start drill",
            StringKey.ReportsEmpty to "Nothing to report yet",
            StringKey.ReportsEmptyHint to "Finish a section and your numbers will show up here.",
            StringKey.ReportsMainVsExtra to "Main vs extra",
            StringKey.ReportsScope to "How many words",
            StringKey.ReportsRun to "Run %d",
            StringKey.ReportsWordsTotal to "%d words",
            StringKey.StatWords to "Words",
            StringKey.ScopeWeakest25 to "Weakest 25",
            StringKey.ScopeWeakest50 to "Weakest 50",
            StringKey.ScopeEverything to "Everything",

            StringKey.RestartTitle to "You've been through everything",
            StringKey.RestartMessage to "Every word has finished a full five-answer cycle. Starting a new run clears the checklists so you can go again — your history and accuracy are kept.",
            StringKey.RestartAction to "Start a new run",
            StringKey.RestartLater to "Not yet",
            StringKey.ExtraLoopTitle to "Full pass complete",
            StringKey.ExtraLoopMessage to "You've drilled every word in this scope. The list starts again from the weakest.",

            StringKey.SettingsTitle to "Settings",
            StringKey.SettingsAppearance to "Appearance",
            StringKey.SettingsTheme to "Theme",
            StringKey.ThemeSystem to "System",
            StringKey.ThemeLight to "Light",
            StringKey.ThemeDark to "Dark",
            StringKey.SettingsLanguage to "Language",
            StringKey.SettingsPronunciation to "Pronunciation",
            StringKey.SettingsAccent to "Accent",
            StringKey.AccentAmerican to "American",
            StringKey.AccentBritish to "British",
            StringKey.AccentAustralian to "Australian",
            StringKey.SettingsSpeed to "Speed",
            StringKey.SpeedSlow to "Slow",
            StringKey.SpeedNormal to "Normal",
            StringKey.SpeedFast to "Fast",
            StringKey.SettingsAutoSpeak to "Speak each new word",
            StringKey.SettingsAutoSpeakHint to "Pronounces a word automatically as it appears.",
            StringKey.SettingsHaptics to "Haptics",
            StringKey.SettingsData to "Data",
            StringKey.SettingsResetProgress to "Reset all progress",
            StringKey.SettingsResetMessage to "This erases every checklist, counter and session record. It cannot be undone.",
            StringKey.SettingsExportProgress to "Export progress",
            StringKey.SettingsImportProgress to "Import progress",
            StringKey.SettingsBackupHint to "Save your practice history to a file before reinstalling, and restore it afterwards.",
            StringKey.BackupRestoreTitle to "Replace current progress?",
            StringKey.BackupRestoreMessage to "Restoring overwrites everything currently saved on this device with the contents of the backup.",
            StringKey.BackupRestoreAction to "Restore",
            StringKey.BackupRestoredTitle to "Progress restored",
            StringKey.BackupExportFailed to "Could not export progress",
            StringKey.BackupImportFailed to "Could not import progress",
            StringKey.CommonOK to "OK",
            StringKey.SettingsPrivacy to "Privacy",
            StringKey.SettingsPrivacyBody to "Everything stays on this device. There is no account, no analytics, no network access of any kind — the word lists are bundled inside the app and your progress is a single file in the app's own storage. Deleting the app deletes all of it.",
            StringKey.SettingsAbout to "About",
            StringKey.SettingsVersion to "Version",
            StringKey.AboutTitle to "About",
            StringKey.AboutBody to "An offline vocabulary trainer built around two classic TOEFL word lists. No account, no subscription, no connection required.",
            StringKey.AboutContentTitle to "Word lists",
            StringKey.AboutContentBody to "504 Absolutely Essential Words (Barron's) and 400 Must-Have Words for the TOEFL (McGraw-Hill). Definitions are study notes, not the publishers' text.",
        )

        /**
         * Second language slot. Any key missing here falls back to English, so
         * this table can be filled in incrementally.
         */
        val persian: Map<StringKey, String> = mapOf(
            StringKey.CommonCancel to "انصراف",
            StringKey.CommonDone to "تمام",
            StringKey.CommonClose to "بستن",
            StringKey.CommonNext to "بعدی",
            StringKey.CommonStart to "شروع",
            StringKey.CommonContinue to "ادامه",
            StringKey.CommonBack to "بازگشت",
            StringKey.CommonReset to "بازنشانی",
            StringKey.CommonQuit to "خروج",
            StringKey.CommonKeepGoing to "ادامه می‌دهم",

            StringKey.TabStudy to "مطالعه",
            StringKey.TabReports to "گزارش‌ها",
            StringKey.TabSettings to "تنظیمات",

            StringKey.LibraryTitle to "کتابخانه",
            StringKey.LibrarySubtitle to "%d واژه در دو کتاب",
            StringKey.LibraryContinue to "ادامه از جایی که بودید",
            StringKey.LibraryEmpty to "واژه‌ای پیدا نشد",
            StringKey.LibraryEmptyHint to "فایل‌های محتوا خوانده نشدند. این یک مشکل بسته‌بندی برنامه است.",

            StringKey.BookSectionsCount to "%d بخش",
            StringKey.BookWordsCount to "%d واژه",
            StringKey.BookStart to "شروع مطالعه",
            StringKey.BookAbout to "درباره این کتاب",
            StringKey.BookProgress to "پیشرفت",

            StringKey.SectionChooseList to "یک فهرست انتخاب کنید",
            StringKey.SectionBegin to "شروع",
            StringKey.SectionAbout to "درباره این بخش",
            StringKey.CategoryMain to "اصلی",
            StringKey.CategoryExtra to "اضافه",
            StringKey.CategoryMainSubtitle to "واژه‌های خود کتاب",
            StringKey.CategoryExtraSubtitle to "واژه‌هایی که خودتان جمع کرده‌اید",
            StringKey.SectionNeedWork to "%d واژه نیاز به تمرین دارد",
            StringKey.SectionNotStarted to "شروع نشده",
            StringKey.SectionComplete to "کامل",
            StringKey.SectionNoExtras to "این بخش واژه اضافه ندارد",

            StringKey.PracticeKnewIt to "بلد بودم",
            StringKey.PracticeDidntKnow to "بلد نبودم",
            StringKey.PracticeMeaning to "معنی",
            StringKey.PracticeNextWord to "واژه بعدی",
            StringKey.PracticeFinish to "پایان",
            StringKey.PracticeProgress to "%d از %d",
            StringKey.PracticeLastFive to "۵ تای قبلی",
            StringKey.PracticeThisCycle to "این دور",
            StringKey.PracticeCycleComplete to "دور کامل شد",
            StringKey.PracticeQuitTitle to "از این تمرین خارج می‌شوید؟",
            StringKey.PracticeQuitMessage to "هرچه تا اینجا پاسخ داده‌اید ذخیره شده است.",
            StringKey.PracticeTapToHear to "برای شنیدن بزنید",
            StringKey.PracticeNewWord to "بار اول",
            StringKey.PracticeCorrectTally to "%d درست",
            StringKey.PracticeWrongTally to "%d غلط",
            StringKey.PracticeSpeakLabel to "تلفظ %s",
            StringKey.PracticeChecklistLabel to "%d از %d پاسخ، %d درست",
            StringKey.PracticeTip to "نکته",

            StringKey.SummaryTitle to "بخش کامل شد",
            StringKey.SummaryAccuracy to "دقت",
            StringKey.SummaryAnswered to "پاسخ‌داده",
            StringKey.SummaryNextSection to "بخش بعدی",
            StringKey.SummaryPracticeAgain to "تمرین دوباره",
            StringKey.SummaryBackToMenu to "بازگشت به منو",
            StringKey.SummaryBookComplete to "این آخرین بخش این کتاب بود.",
            StringKey.SummaryHeadline to "آفرین",

            StringKey.ReportsTitle to "گزارش‌ها",
            StringKey.ReportsOverall to "کلی",
            StringKey.ReportsMastered to "مسلط",
            StringKey.ReportsSeen to "دیده‌شده",
            StringKey.ReportsAccuracy to "دقت",
            StringKey.ReportsByBook to "به تفکیک کتاب",
            StringKey.ReportsWeakest to "بیشترین نیاز به تمرین",
            StringKey.ReportsRecent to "تمرین‌های اخیر",
            StringKey.ReportsExtraPractice to "تمرین اضافه",
            StringKey.ReportsExtraSubtitle to "ضعیف‌ترین واژه‌های کل کتابخانه را تمرین کنید. جدا از پیشرفت اصلی حساب می‌شود.",
            StringKey.ReportsStartDrill to "شروع تمرین",
            StringKey.ReportsEmpty to "هنوز گزارشی نیست",
            StringKey.ReportsEmptyHint to "یک بخش را تمام کنید تا آمارتان اینجا بیاید.",
            StringKey.ReportsMainVsExtra to "اصلی در برابر اضافه",
            StringKey.ReportsScope to "چند واژه",
            StringKey.ReportsRun to "دور %d",
            StringKey.ReportsWordsTotal to "%d واژه",
            StringKey.StatWords to "واژه‌ها",
            StringKey.ScopeWeakest25 to "۲۵ واژه ضعیف",
            StringKey.ScopeWeakest50 to "۵۰ واژه ضعیف",
            StringKey.ScopeEverything to "همه",

            StringKey.RestartTitle to "همه واژه‌ها را تمام کردید",
            StringKey.RestartMessage to "هر واژه یک دور کامل پنج‌تایی را تمام کرده است. شروع دور تازه چک‌لیست‌ها را پاک می‌کند اما تاریخچه و دقت شما می‌ماند.",
            StringKey.RestartAction to "شروع دور تازه",
            StringKey.RestartLater to "فعلاً نه",
            StringKey.ExtraLoopTitle to "یک دور کامل تمام شد",
            StringKey.ExtraLoopMessage to "همه واژه‌های این محدوده را تمرین کردید. فهرست دوباره از ضعیف‌ترین شروع می‌شود.",

            StringKey.SettingsTitle to "تنظیمات",
            StringKey.SettingsAppearance to "ظاهر",
            StringKey.SettingsTheme to "پوسته",
            StringKey.ThemeSystem to "سیستم",
            StringKey.ThemeLight to "روشن",
            StringKey.ThemeDark to "تیره",
            StringKey.SettingsLanguage to "زبان",
            StringKey.SettingsPronunciation to "تلفظ",
            StringKey.SettingsAccent to "لهجه",
            StringKey.AccentAmerican to "آمریکایی",
            StringKey.AccentBritish to "بریتانیایی",
            StringKey.AccentAustralian to "استرالیایی",
            StringKey.SettingsSpeed to "سرعت",
            StringKey.SpeedSlow to "آهسته",
            StringKey.SpeedNormal to "معمولی",
            StringKey.SpeedFast to "تند",
            StringKey.SettingsAutoSpeak to "تلفظ خودکار هر واژه",
            StringKey.SettingsAutoSpeakHint to "هر واژه به‌محض نمایش خوانده می‌شود.",
            StringKey.SettingsHaptics to "لرزش",
            StringKey.SettingsData to "داده‌ها",
            StringKey.SettingsResetProgress to "بازنشانی همه پیشرفت‌ها",
            StringKey.SettingsResetMessage to "همه چک‌لیست‌ها، شمارنده‌ها و تاریخچه پاک می‌شود. قابل بازگشت نیست.",
            StringKey.SettingsExportProgress to "خروجی گرفتن از پیشرفت",
            StringKey.SettingsImportProgress to "بازیابی پیشرفت",
            StringKey.SettingsBackupHint to "پیش از نصب دوباره برنامه، تاریخچه تمرین را در یک فایل ذخیره کنید و بعد آن را برگردانید.",
            StringKey.BackupRestoreTitle to "پیشرفت فعلی جایگزین شود؟",
            StringKey.BackupRestoreMessage to "بازیابی، همه چیزی که اکنون روی این دستگاه ذخیره شده را با محتوای فایل پشتیبان جایگزین می‌کند.",
            StringKey.BackupRestoreAction to "بازیابی",
            StringKey.BackupRestoredTitle to "پیشرفت بازیابی شد",
            StringKey.BackupExportFailed to "خروجی گرفتن انجام نشد",
            StringKey.BackupImportFailed to "بازیابی انجام نشد",
            StringKey.CommonOK to "باشه",
            StringKey.SettingsPrivacy to "حریم خصوصی",
            StringKey.SettingsPrivacyBody to "همه چیز روی همین دستگاه می‌ماند. نه حسابی هست، نه آماری، نه هیچ ارتباط شبکه‌ای — واژه‌ها داخل خود برنامه هستند و پیشرفت شما یک فایل در حافظه اختصاصی برنامه است. با حذف برنامه همه‌اش پاک می‌شود.",
            StringKey.SettingsAbout to "درباره",
            StringKey.SettingsVersion to "نسخه",
            StringKey.AboutTitle to "درباره",
            StringKey.AboutBody to "یک تمرین‌کننده واژگان آفلاین بر پایه دو فهرست کلاسیک واژگان تافل. بدون حساب کاربری، بدون اشتراک، بدون نیاز به اینترنت.",
            StringKey.AboutContentTitle to "فهرست واژه‌ها",
            StringKey.AboutContentBody to "۵۰۴ واژه کاملاً ضروری (Barron's) و ۴۰۰ واژه ضروری تافل (McGraw-Hill). معنی‌ها یادداشت مطالعه‌اند، نه متن ناشر.",
        )
    }
}
