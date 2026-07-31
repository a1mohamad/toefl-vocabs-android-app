package io.github.a1mohamad.toeflvocab.core.models

/**
 * Icon identities, named once and resolved to a real drawable exactly once.
 *
 * The iOS build stores SF Symbol names as raw strings on the model types and
 * lets `Image(systemName:)` resolve them at the call site. A typo there is a
 * blank square that nothing catches until a screenshot comes back from CI.
 * Since the whole icon set had to be re-chosen for Material anyway, it is an
 * enum here: the model layer still names the *idea* of an icon and stays free of
 * any Compose import, but a wrong name is now a compile error.
 *
 * The mapping to actual vectors lives in `designsystem/Icons.kt`.
 */
enum class AppSymbol {
    // Tabs
    Study,
    Reports,
    Settings,

    // Content
    BookClosed,
    Sparkles,
    Sparkle,
    Calendar,
    Review,
    Layers,
    Alphabet,
    AppMark,

    // Navigation and state
    ChevronForward,
    PlayCircle,
    Checkmark,
    Xmark,
    CheckmarkCircle,
    XmarkCircle,
    CheckmarkSeal,
    ExclamationCircle,
    ExclamationTriangle,
    RadioSelected,
    RadioUnselected,

    // Theme
    ThemeSystem,
    ThemeLight,
    ThemeDark,

    // Practice and reports
    SpeakerWave2,
    SpeakerWave3,
    Bolt,
    FlagCheckered,
    CycleGrid,
    ChartDocument,
    QuestionFolder,
    BooksVertical,

    // Settings
    Info,
    Trash,
    ExportUp,
    ImportDown,
    SpeedSlow,
    SpeedFast,
}
