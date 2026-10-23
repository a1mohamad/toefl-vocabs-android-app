# TOEFL Vocab — Project Plan

Offline Android vocabulary trainer built around three word lists: **504 Absolutely
Essential Words** (Barron's), its **125 Hard but Essential Words** appendix,
and **400 Must-Have Words for the TOEFL** (McGraw-Hill) — 1,762 words across
95 sections. No account, no network, no subscription.

Built under fixed constraints: Windows-only development, **no Android SDK and no
Android Studio on the machine**, no local emulator, and therefore no compile step
outside CI. Gradle runs only on GitHub Actions, and the wrapper jar is not
committed. Every decision below is shaped by those.

This is a screen-for-screen port of the [SwiftUI
original](https://github.com/a1mohamad/toefl-vocabs-ios-app). The rules, the
numbers and the copy are shared; this document covers the Android build, and the
README's Porting Notes list the places the two genuinely differ.

---

## 1. Decisions worth knowing about

These are the calls that were made deliberately rather than by default.

| Decision | Why |
|---|---|
| **A JSON file, not Room** | Room is a schema, a DAO and a compiler plugin for what is one small document rewritten whole on every change. A migration bug would cost a full CI round trip to even see, with no local emulator. `ProgressState` decodes field by field with defaults, so an old file never fails to load — and it stays byte-compatible with the iOS build, which is what lets Export/Import move between the two. |
| **`minSdk 26`, `targetSdk 35`** | Android 8.0 covers effectively every device still in use, and nothing in the feature set needs newer. Nothing here is gated on a recent API. |
| **Words stored as arrays, not objects** | A JSON *object* has no defined key order. `kotlinx.serialization` happens to preserve insertion order today, but the format promises nothing and the iOS build cannot rely on it at all. The order lives in the data instead, so both apps present the same queue. The legacy `{term: definition}` form still loads (alphabetised) so nothing breaks. |
| **`catalog.json` alongside `vocabs.json`** | Same problem one level up: books and sections are objects. No sort function would put `504/review_1` between `day_6` and `day_7` where it belongs. The catalog states order explicitly and is also where intro/preview copy lives. |
| **Localisation as a Kotlin table, not `strings.xml`** | The app switches language *in app*, which resource qualifiers do not do without recreating the activity. A mis-scoped qualifier also fails as raw keys on screen, which only shows up after a full CI round trip. One `Map` cannot fail to build and is unit-tested. The only string in `res/values/strings.xml` is the launcher label, because Android reads it from the manifest before any Kotlin runs. |
| **`TextToSpeech`, no audio assets** | US/UK/AU are system voices. Zero bundled audio, a smaller APK, and a word added to `vocabs.json` is instantly pronounceable. An engine can report `LANG_MISSING_DATA` for a locale the user never downloaded, so the accent falls back through the other two rather than going silent. |
| **Colours in code, not `colors.xml` plus a `values-night` twin** | Two resource files that have to stay in lockstep is one more thing only a CI round trip can verify. A pair of literals on one line cannot drift, and it reacts to the in-app theme setting rather than only to the system one. |
| **Practice is a full-screen overlay, not a route** | A session is a self-contained task with its own quit control. Presenting it above the tab scaffold, rather than as another entry in the back stack, is what stops a half-finished session being abandoned inside a navigation path. |

---

## 2. Data model

### Content (immutable, loaded once at launch)

```
VocabCatalog
└── Book            id, title, shortTitle, author, intro, theme, order
    └── VocabSection  id, title, intro, kind (.lesson/.review), order
        └── [VocabCategory: [VocabItem]]     .main / .extra
            └── VocabItem   id, term, definition, usageTips, orderIndex
```

`definition` holds only the meaning. Two markers in the source string are read at
load time rather than shown raw:

| In the data | Becomes | Shown as |
|---|---|---|
| `;` inside the definition | `meanings`, derived on read | a numbered list, one meaning per line |
| ` --- ` | one `usageTips` entry per marker | a lightbulb row under the meaning |
| `'word'` inside a tip | a `TipMarkup.Segment` with a highlight index | coloured and bolded inline |

`meanings` is derived rather than stored so it can never disagree with
`definition`, and the split lives on the model rather than in the view so the
screen and the screen reader always read the same list. `usageTips` is a list,
not one string: four words in the 400 list carry two independent notes, and
gluing them into one paragraph loses the fact that they are two.

`TipMarkup` is the one piece of this with a non-obvious rule. A `'` opens a
highlight only at a word boundary; otherwise the apostrophe in `"someone's
'ability' to"` would open a quote, swallow `s ` and colour the wrong run. It is a
character-for-character port of the Swift original and is unit-tested against the
same cases, because the two apps have to highlight the same words.

`VocabID` is `book/section/category/term` — a single string, human-readable in
the saved file. Scoping by all four matters: `abandon`, `circulate`, `feature`
and `survive` each appear in more than one place in the source data, and each
occurrence gets its own progress record.

`availableCategories` is computed from what actually has words, so `504/review_1`
(which has no extras) never offers an empty list.

### Progress (mutable, persisted)

```
ProgressState
├── schemaVersion
├── main:  [String: WordStats]     keyed by VocabID.rawValue
├── extra: [String: WordStats]     the drill's separate counters
├── sessions: [SessionRecord]      capped at 250
├── runNumber                      increments on full restart
└── lastLocation                   powers "continue where you left off"
```

`WordStats` per word per mode:

| Field | Meaning |
|---|---|
| `attempts`, `correct`, `incorrect` | lifetime totals, never reset |
| `currentCycle: [Bool]` | the five boxes, filling left to right |
| `lastCycle: [Bool]?` | the banked previous five |
| `completedCycles` | lifetime |
| `completedCyclesThisRun` | cleared on restart; drives completion |
| `consecutiveCorrect` | mastery streak |
| `lastAnsweredAt` | recency |

Two maps keyed by `String` rather than one keyed by `VocabID`: a
`Map<VocabID, WordStats>` serialises as a flat JSON *array* of alternating keys
and values, because `VocabID` is not a primitive key. Keeping them as strings is
also what keeps the file interchangeable with the iOS build's.

### Settings

`AppSettings` — theme, language, accent, speech rate, auto-speak, haptics, drill
scope. Persisted as one encoded blob in `SharedPreferences`, so adding a setting
never needs a migration.

`AppSettings` is an immutable `data class`, so `clampedSpeechRate` is an accessor
rather than something rewritten in `init` — a `val` cannot be reassigned there.
Every read site uses the accessor, which keeps a hand-edited file as safe as the
slider.

---

## 3. Core algorithms

### 3.1 The five-step reset rule

The checklist holds five boxes. Each answer appends. The instant the fifth lands:

```
lastCycle = currentCycle      // banked for the recap
currentCycle = []             // fresh checklist
completedCycles += 1
completedCyclesThisRun += 1
```

One deliberate refinement: the banked cycle **stays on screen** (flagged
`isRecap`, rendered dimmed and captioned "Last 5") until the user answers the
next word. Resetting the row the moment the fifth box lands would mean the user
never sees their own fifth answer — the row would blank out under their finger.

A word counts as **completed** for the current run once it has banked one cycle,
and **mastered** when it has banked a cycle *and* is on a streak of 3+ correct.

Decoding repairs a `currentCycle` of five or more (from a hand-edited or
truncated file) rather than leaving a checklist that can never reset.

### 3.2 Adaptive ordering

The promise: *open a section and the words you get wrong most come first.*

```kotlin
score = smoothedErrorRate                                  // (incorrect + 0.5) / (attempts + 1)
      + 0.35  if the last answer was wrong
      - 0.06 × min(consecutiveCorrect, 5)
      - 0.04 × min(completedCyclesThisRun, 5)
```

Laplace smoothing puts an **unseen word at exactly 0.5** — below anything you
have actually got wrong, above anything you have been getting right. That single
choice produces all the behaviour that matters:

- A fresh section: every word scores 0.5, ties break on `orderIndex`, so it plays
  in **book order**.
- One wrong answer: `(1 + 0.5) / 2 + 0.35 = 1.1` → straight to the front.
- Three right answers: `0.125 − 0.18 < 0` → sinks below the unseen words.
- Nine right then one wrong still leads the queue — a recent mistake outweighs a
  good long-term record.

Sorting is **deterministic**: no randomness, and the comparator falls through
`score → orderIndex → id` to a total ordering. `sortedWith` is stable, so a
partial comparator would not reshuffle here — but the iOS build's `sort` is not,
and a queue that differs between the two apps on the same history would be a bug
in one of them. The total ordering is what makes them identical.

The queue is built once when a session starts and is **not** re-sorted mid-session
— re-ranking after every answer would make words jump around under the user.
Re-ordering happens on the next entry, which is exactly what was asked for.

### 3.3 Extra practice (the drill)

Launched from Reports. Ranked by **main-mode** weakness, because that is where
the real learning history lives, then by fewest drill attempts so a long session
keeps moving instead of looping over the same handful.

Answers are recorded into the `extra` dictionary only, so the drill can never
move main progress. Both counters are shown on the card ("3 right · 5 wrong").

Running the whole list weakest-to-strongest gives the required behaviour for
free: wrong words first, then the correct ones, and when the pass ends the
summary says so and the list starts again from the weakest. Scope is
`Weakest 25 / Weakest 50 / Everything`.

### 3.4 Global completion and restart

`allWordsCompleted` is true only when **every** word in the library has banked a
five-answer cycle in the current run. When it flips true, the summary screen
offers a restart: `beginNewRun()` clears `completedCyclesThisRun` and the current
checklists everywhere, increments `runNumber`, and keeps lifetime totals, banked
recaps and session history so Reports keeps its history.

---

## 4. Screen by screen

| # | Screen | Contents |
|---|---|---|
| 1 | **Library** (root) | Title with total word count · "continue where you left off" card · one card per book: gradient spine, intro excerpt, progress meter, section/word chips |
| 2 | **Book intro** | Gradient hero (title, author, progress ring, words/seen/mastered) · full intro copy · section list: kind icon, progress meter, word count, "n need work" chip, completion seal |
| 3 | **Section intro** | Section title, kind, intro copy · Main/Extra picker cards with counts and weak-word chips (Extra hidden with an explanatory line where the section has none) · **queue preview** showing the first three words that will actually come up · Begin |
| 4 | **Practice** | Top bar: quit ✕, title, `3 of 12`, progress meter · Word card: large serif term, pronunciation control (speaker + US/UK/AU badge), five-box checklist with caption, first-time / tally chips · Answer: **Didn't know** / **I knew it** · then the meaning revealed — numbered one per line where the book gives several — plus a tip card with one lightbulb per usage note, and **Next word** |
| 5 | **Summary** | Accuracy ring, answered, cycles completed · drill-loop notice or global-restart notice when applicable · **Next section** / **Practise again** / **Back to menu** |
| 6 | **Reports** | Overview card (mastery ring, accuracy, seen, needs-work) · drill launcher with scope picker · per book: accuracy, meter, **section heat grid** · "needs the most work" list with tallies and compact checklists · recent-session bar chart · main/extra/drill split · run number |
| 7 | **Settings** | Theme · Language · Accent · Speed slider with audible preview · auto-speak · haptics · reset progress (confirmed) · privacy footer · About · version |
| 8 | **About** | App card, word-list credits, privacy statement, per-book word counts |

The section **heat grid** is the answer to "not a long list": ninety-five
sections across three books would be an unreadable stack of rows. As a 6-column grid of
cells shaded by accuracy — neutral where untouched, so "not started" never reads
as "doing badly" — it is one glance.

### Navigation

```
RootScreen
├── Study     studyPath: List<Route> → Library → Book → Section
├── Reports   Reports
└── Settings  settingsPath: List<Route> → Settings → About
     +
AnimatedVisibility(router.activePractice != null) → Practice → Summary
```

`Router` owns the tab, both paths and the active session, all as Compose state.
Paths are plain lists rather than a navigation graph: the app rewrites them
wholesale — "back to menu" empties one — and a graph would turn those single
state changes into multi-step animations.

`BackHandler` at the root pops the current tab's path and only falls through to
finishing the activity once it is empty. Practice sits above the scaffold as a
full-screen overlay with its own quit control.

---

## 5. Architecture

MVVM, with the router standing in for a coordinator.

```
app/           MainActivity, ContentProvider, RootScreen, Chrome, ScreenshotHarness
navigation/    Router, Route, PracticeConfiguration
core/
  models/      ContentModels, PracticeModels, AppSettings, AppSymbol
  content/     VocabCatalogLoader, TipMarkup
  persistence/ ProgressStore, SettingsStore, ProgressBackup
  engine/      AdaptiveOrdering, StatsAggregator
  audio/       PronunciationService, Haptics
  localization/Strings, LocalStrings, LayoutDirectionBridge
designsystem/  Theme (palette, typography, metrics), Components, Icons, SegmentedPicker
features/      library, book, section, practice, reports, settings
```

Five objects are built once in `MainActivity.onCreate` and handed down through
`CompositionLocalProvider`: `ContentProvider`, `ProgressStore`, `SettingsStore`,
`PronunciationService`, `Router`. There is no DI framework; five constructor
calls do not need one.

Values are immutable `data class`es and `record()` returns a new one. Swift gets
value semantics for free; in Kotlin a shared mutable record would both alias into
the progress map and fail to trigger recomposition.

Only `PracticeScreen` has a dedicated view model, because it is the only screen
with real state. Everything else derives from the stores and stays a pure
function of them — `StatsAggregator` is synchronous and deterministic, so the
whole Reports screen is one call with no cache to invalidate. The library is a
few hundred words; a full recompute per render is cheaper than any caching would
be to maintain.

Content failures degrade instead of crashing: a broken row is skipped, a missing
catalog falls back to generated titles, a section absent from the catalog is
appended rather than hidden, and an unreadable file shows an explanation while
Settings keeps working. The APK is installed from a Releases page, not the Play
Store, so there is no crash reporting and no console — a crash on a packaging
mistake would be the worst possible outcome.

---

## 6. Persistence

- `filesDir/progress.json`, written to a `.tmp` sibling and then renamed, so a
  kill mid-write cannot leave a half-written file.
- Writes debounced ~600 ms and flushed on `Lifecycle.Event.ON_STOP`, and
  immediately on session end, restart and reset.
- A file that fails to decode is **quarantined** (renamed with a timestamp), not
  deleted — the user gets a working app and the original is still there.
- `SharedPreferences` holds settings only.
- `filesDir` is covered by Android's auto-backup rules, so a device-to-device
  transfer carries progress across without the user doing anything.

### Optional remote sync, if it is ever wanted

Not built, and not needed — but the shape is deliberate. `ProgressState` is one
`@Serializable` value with a `schemaVersion`, so sync is: last-writer-wins on the
whole document for a first pass, or per-word merge taking `max(attempts)` and the
later `lastAnsweredAt` for a proper one. `WordStats` is already a CRDT-friendly
shape (monotonic counters plus a timestamp). Any sync would be a plain HTTPS
endpoint rather than a platform service, because the same file has to stay
readable by the iOS build. The content layer would stay bundled either way.

---

## 7. Accessibility

- **Text scaling everywhere.** Sizes are in `sp`, so every one of them follows
  the system font-size setting. No `dp` text in body copy.
- **Colour is never the only signal.** Right/wrong carries a ✓/✗ glyph in the
  checklist, in the answer buttons and in the reveal. Tip highlights are bolded
  as well as coloured.
- **TalkBack.** The five-box strip is one node reading "3 of 5 answered, 2
  correct" rather than five anonymous shapes, through `clearAndSetSemantics`.
  Cards merge their children. Heat grid cells announce section, accuracy and
  coverage. A tip reads without its quote marks, since a spoken apostrophe
  carries no meaning.
- **Targets** ≥ 48dp, Android's minimum rather than Apple's 44pt.
- **Reduced motion** — Android has no single switch, so the app reads the
  animator duration scale and treats 0 as "off", disabling the reveal spring and
  the speaking pulse.
- **Haptics** go through `View.performHapticFeedback`, which is the API that
  respects the system-wide haptics setting and needs no permission.
- **Contrast.** Light-mode book gradients are deep (`#4F46E5`, `#0F766E`) because
  white text and glyphs sit on them; the lighter mid-tones that look good on a
  dark background fail contrast on a white one.

## 8. Internationalisation

- `Strings` resolves a `StringKey` against the active language, falling back to
  English for any gap — a missing translation shows readable copy, never a raw
  key. Tests assert both tables are complete and that no key renders as its own
  identifier.
- English and Persian ship. Persian provides `LocalLayoutDirection = Rtl`
  app-wide through `LayoutDirectionBridge`; the chevrons and the back arrow use
  auto-mirrored icons so they flip with it.
- Content (definitions, book and section intros) is **not** localised — it lives
  in the data files.
- To add a language: add a case to `AppLanguage`, add a table, extend the switch
  in `Strings.subscript`.

---

## 9. Running it

There is no local build — that is the constraint, not an oversight. The loop is:

```bash
python Scripts/validate_content.py
```

Runs on Windows in about a second. Validates both files, checks catalog/vocabs
consistency, finds duplicate terms, empty definitions and stray whitespace, and
prints a per-section word-count table. Run it before every push; it catches the
most likely breakage without spending a CI minute or an emulator boot.

```bash
python Scripts/migrate_vocabs.py
```

Converts any legacy `{term: definition}` block into ordered arrays. Run it
whenever new content is pasted in the old style.

Then push. CI does the rest.

### Adding a day of vocabulary

1. Append the words to `app/src/main/assets/VocabData/vocabs.json` under the
   book, as `[{ "term": ..., "definition": ... }]`.
2. Add one entry to `catalog.json`'s `sections` array for title, kind and intro.
   *(Skip this and the app still works — the section is appended last with a
   generated title like "Day 9".)*
3. `python Scripts/validate_content.py`
4. Commit. No Kotlin changes. No Gradle changes.

Both JSON files are kept byte-identical to the iOS build's, so the same edit is
copied across rather than re-made.

### Cutting a release build

Tags are annotated; `<next>` is the version being shipped.

```bash
git tag -a v<next> -m "v<next>"
git push origin v<next>
```

That triggers `release-build`, which restores the keystore from Actions secrets,
runs `assembleRelease` and verifies the signature. Download
`TOEFLVocab-<tag>.apk` from the run and attach it to a GitHub Release.

**Every build must be signed with the same keystore.** An APK signed with any
other key cannot install over an existing one — Android rejects it with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only way through is to uninstall,
which deletes the user's progress.

---

## 10. CI

`.github/workflows/ci.yml`, four jobs, all on ubuntu:

| Job | When | Does |
|---|---|---|
| `content-lint` | every push/PR | `validate_content.py --strict`. ~15s, and it gates everything else so a bad JSON edit never costs a Gradle build or an emulator boot. |
| `unit-tests` | every push/PR | `gradle testDebugUnitTest`. Own job, so a test failure still lets the screenshot job produce a picture. |
| `emulator-check` | every push/PR | `assembleDebug` → **verify `vocabs.json`, `catalog.json` and the launcher icon are actually inside the APK** → boot an API 34 emulator → screenshot every screen in **light and dark**. |
| `release-build` | tags `v*` only | Keystore restored from secrets → `assembleRelease` → `apksigner verify --print-certs` → `TOEFLVocab-<tag>.apk` artifact → keystore deleted. |

The APK-content check exists because the app builds, launches and screenshots
perfectly happily with an empty `assets/` folder — it would just show "No
vocabulary found", which reads like a UI bug rather than a packaging one. That
would otherwise burn a full round trip to diagnose.

The Gradle **wrapper jar is not committed**; `gradle/actions/setup-gradle` pins
the version instead. `gradle wrapper` regenerates it locally for anyone who wants
`./gradlew`.

The emulator needs KVM enabled explicitly, or it falls back to software rendering
and every screenshot step times out.

Screenshot capture lives in `Scripts/capture_screenshots.sh` rather than inline,
because `reactivecircus/android-emulator-runner` runs its `script:` input **one
line per `sh -c` invocation**: a variable assigned on one line is gone by the
next, and a multi-line `for` loop is a syntax error in its first line. The script
also re-shoots any frame that comes back under ~45 KB, because a capture of the
launch window is a valid PNG of nothing and the file count cannot catch it.

A debug-only harness (`app/ScreenshotHarness.kt`, behind `BuildConfig.DEBUG`,
which R8 folds away in release) opens the app straight to a named screen with
seeded progress, so Reports and the checklists are never photographed empty. It
takes an intent extra rather than a launch argument, because that is what
`am start` can pass.

---

## 11. Not built, deliberately

- **Typing or multiple-choice answering.** The flow is self-graded recall, which
  is what was specified and what actually matches how these books are used.
- **Spaced repetition with real intervals (SM-2 etc.).** The weakness score is a
  simpler model that is explainable to the user and testable. Worth revisiting
  once there is enough history to know whether it helps.
- **Per-word notes, favourites, search.** Easy to add on top of `VocabItem`.
- **Widgets, notifications, cloud sync.** All three need a server or a platform
  account; the app makes no network calls at all, by design.
- **Play Store distribution.** The APK is installed from the Releases page, which
  means no automatic updates and a Play Protect warning about an unrecognised
  developer on first install.

## 12. Content corrections

The transcription the content came from carried a long tail of typos. As of 2.0
they have been corrected in one pass: **152** spelling fixes across terms and
definitions (`smoething`, `imporment`, `loswest`, `annoint`, `liquiad`,
`machinary`, `philantrophies` and so on), plus **15** tip markers that had been
typed as `—-` or `--` instead of `---` and were therefore never recognised as
tips at all — their advice was being shown as part of the meaning.

Fourteen of those fixes touch a **term** rather than a definition
(`salughter` → `slaughter`, `cloack` → `cloak`, `elsusive` → `elusive`,
`further tought` → `further thought` and others). A term is part of a word's
identity, so those words start their progress history over. That was judged the
right trade: a misspelled word is worse than practised.

The validator still does not spell-check — it checks shape, duplicates and
catalog agreement. A spelling regression would get in the same way this batch
did, so the content pass is worth repeating whenever a large block of new words
is pasted in. Two things were deliberately left alone, since they are wording
rather than spelling: the `generation` tip in 400/day_3 ends on a dangling
"and", and the `exclusive` tip in 400/day_33 has a close parenthesis with no
opening one.
