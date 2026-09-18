# IZAVO V2.1 implementation report

Implementation and build validation: 5 September 2026.

## Personalization and onboarding

The existing CurrencyPreferences model/repository now stores `displayName` in the existing `currency_preferences` SharedPreferences file. Saving trims surrounding whitespace and caps names at 40 Unicode code points without splitting surrogate pairs. Blank names are supported. No account, profile database, network flow, or upload was added. Name edits preserve onboarding completion, currencies and Show seconds.

Device-local greeting rules are 05:00–11:59 Good morning, 12:00–16:59 Good afternoon, and 17:00–04:59 Good evening. A nonblank name follows a comma; blank names have no comma. Home refreshes on resume and time/time-zone broadcasts, with minute broadcasts updating state only when the hour/date changes. There is no per-second timer.

Onboarding retains the initial currency setup, then presents “What should we call you?” and “This stays on your device.” The borderless Unicode name input supports IME Done, Continue and Skip. The view scrolls above keyboard insets; Back returns to currency setup. Configuration changes preserve name and currency drafts.

Completion saves preferences before any transition. Presentation is disposable, so an interrupted committed setup opens Home next time. Editing a name does not replay completion. A pending Quick Settings request waits until completion has finished and is consumed once.

Nominal completion timing at normal system animation scale:

- 0–210 ms: keyboard dismissed, one confirmation haptic, name screen fades and rises up to 5dp.
- 210–1790 ms: disposable greeting overlay above Home.
- Approximately 400–684 ms: greeting fades in (“Nice to meet you, Adam.” or “Make yourself at home.”).
- Approximately 826–1142 ms: “Your money, made clearer.” fades in.
- Approximately 1316–1790 ms: overlay fades to Home while its content rises gently.

The atmosphere is static; its appearance comes from the overlay fade. Platform-disabled animations skip the greeting sequence on API 26+; Compose duration scaling applies to regular animations. Device validation of timing at altered system scales is pending.

## Home and financial composition

Home now includes a restrained personalized greeting, month, spending label, associated currency above an adaptive tabular amount, comparison and sparkline. Empty current months say “Nothing spent this month.” and “A quiet month so far.” Older recent expenses remain separate from the current-month summary.

The hero adds cyan at 24% opacity, blue at 9.5%, and lavender at 11%, with radial radii of 84%, 66% and 60% of the available width. The neutral page remains intact. MoneyDisplay measures the actual text width and reduces the hero size when needed, including long amounts and enlarged fonts. Amount text explicitly uses LTR direction; user titles/names use content-derived direction.

Money changes crossfade with a 6px vertical offset over 210ms. Values remain exact final values throughout the transition; there is no digit counter or financial interpolation.

## Navigation and interactions

The old navigation rectangle came from plain `Modifier.clickable`, which inherited the default Material indication across each allocated destination slot. SettingsRow used the same pattern.

The shared `premiumClick` uses a remembered interaction source and no default indication. It preserves click/keyboard handling and semantics. Press uses 90ms compression and 210ms return; navigation scales to 0.95, Add to 0.94, normal controls to 0.98. Transaction and settings rows retain scale 1 and use reduced opacity. Hover has no filled state layer. Keyboard focus draws a visible cyan 2dp outline with 18dp rounded corners. The outline follows the rounded content target; no square background is painted.

Navigation exposes Role.Tab and selected semantics. Selected destinations retain charcoal text/icon and a 4dp cyan indicator, which fades/scales over 210ms. The 48dp charcoal Add remains absolutely centered. The navigation surface keeps its 30dp radius and top highlight; shadow elevation is reduced from 7dp to 5dp. Normal-size destination hit targets remain at least 48dp.

Main destinations fade with a small direction-aware 12px horizontal offset over 240ms. Category Detail uses a 24px offset and reverse direction on Back. Re-tapping the current navigation destination does not reset Statistics or vibrate.

## Statistics and other screens

Statistics uses the shared financial composition, quieter compact period/currency controls, a softly animated segmented surface, lighter metric surfaces, more breathing room for category rows, and smaller quiet insight dots with larger spacing. Category list taps still open detail. Ring taps now retain selection in the ring so its center can show a category amount and share; adjacent rows remain the route to Category Detail.

The previous chart reveal used a constant 1f target and never actually animated. Charts now restart a 360ms reveal on data changes. Bars grow, ring sweeps reveal, and lines rise/fade with a faint gradient underneath. Bar zero values do not draw misleading stubs. Axis edge labels are aligned inside the canvas. The chart callout reserves height to avoid jumping when selected. Line tap/drag selection uses the nearest point. Custom accessibility actions move to previous/next chart values; horizontal drag remains distinct from vertical scrolling.

Category iconography reuses the existing central ExpenseCategoryVisuals mapping: dining, car, shopping bag, receipt, card and ellipsis. Shared choice controls now show those icons for recognized categories, propagating to Quick Add/Edit, History filters and Import review. Home, transaction rows and Statistics already used this mapping. Detail now includes its mapped category icon and the shared responsive amount. Detail and period lists scroll for smaller screens.

History search has a restrained cyan focus border with no geometry change. Its filter control uses the shared press treatment. Existing query/focus clearing around filters is retained.

Settings adds Profile → Name, opening a keyboard-safe name sheet. Save updates local preferences without replaying onboarding. The bottom signature is centered IZAVO, “Your money, made clearer.” and the dynamic version/Beta text, separate from feedback.

Quick Add/Edit retain the native ModalBottomSheet entrance/gesture spring, keyboard rules, validation and save callbacks. Category changes add a light haptic; metadata rows use the shared interaction. A custom fixed-duration sheet animation was not introduced: the Sheet=300ms token is available, but native ModalBottomSheet controls its own motion. Save dismissal still follows the existing save flow rather than delaying data handling for animation.

## Motion, material and haptics

IzavoMotion tokens: Press 90ms, Fast 130ms, Control 210ms, Screen 240ms, Chart 360ms, Sheet 300ms, Completion overlay 1580ms. ExpenseMotion forwards existing call sites to the corresponding shared tokens. No infinite motion is added.

IzavoMaterial defines navigation, segmented track, sheet, floating control, tooltip, highlight and radii. Opaque sheet fallback is retained to avoid the earlier content-bleed bug. No backdrop-blur dependency was added.

Light TextHandleMove feedback is used for changed navigation destinations, Add, category changes, and confirmed import groups. Existing save/delete LongPress feedback remains. Onboarding acceptance uses one LongPress confirmation. No hover, scrolling or chart-point haptics were added. Physical strength depends on Android/OEM settings.

## Validation

- Kotlin compilation: passed.
- Room/KSP processing: passed, schema export remains enabled.
- Debug JVM suite: 78 tests, zero failures/errors (68 existing plus 10 name/greeting tests).
- Existing Statistics, Smart Import/parser and CSV/Unicode/BOM tests remain passing.
- Android test sources: compiled successfully, including the existing migration test.
- Debug APK: assembled successfully.
- APK: `E:\MobileProjects\app\build\outputs\apk\debug\app-debug.apk`.

Added JVM coverage includes trimming, blank fallback, Unicode, emoji-safe length limiting and every greeting boundary. Added Android coverage includes preference persistence, name editing without onboarding/seconds/currency reset, navigation selection/Add callback, Unicode IME entry, completion callback, empty Home and a USD 1,000,000 hero without a fabricated MVR total. The Statistics empty-state test was updated for the new copy.

No connected Android device or configured AVD was available. Instrumentation, migration execution, screenshots and physical QA were therefore not performed. Samsung keyboard geometry, press/hover/focus renders, enlarged-font navigation, RTL shaping, TalkBack behavior, completion timing and haptic feel remain device QA items. Compiled UI tests are not a claim of device execution. Quick Add native sheet timing was retained rather than measured or overridden.

Room schema/version/migrations: unchanged, version 4. Manifest/permissions: unchanged. Production dependencies: none added. Existing functionality intentionally removed: none. Statistics math, import decisions, entity data, CSV and feedback formats were not rewritten.

## Files created

Paths are relative to `E:\MobileProjects`.

- `app/src/main/java/com/izavo/app/preferences/DisplayName.kt`
- `app/src/main/java/com/izavo/app/ui/design/IzavoPolish.kt`
- `app/src/main/java/com/izavo/app/ui/design/NameEditor.kt`
- `app/src/main/java/com/izavo/app/ui/design/HeroAtmosphere.kt`
- `app/src/main/java/com/izavo/app/ui/onboarding/OnboardingCompletion.kt`
- `app/src/main/java/com/izavo/app/ui/home/HomeClock.kt`
- `app/src/main/java/com/izavo/app/ui/settings/DisplayNameSheet.kt`
- `app/src/test/java/com/izavo/app/preferences/DisplayNameTest.kt`
- `app/src/androidTest/java/com/izavo/app/DisplayNamePreferenceTest.kt`
- `app/src/androidTest/java/com/izavo/app/ui/RefinementUiTest.kt`
- `IZAVO-V2.1-REPORT.md`

## Files modified

- `app/src/main/java/com/izavo/app/preferences/CurrencyPreferences.kt`
- `app/src/main/java/com/izavo/app/preferences/CurrencyPreferencesRepository.kt`
- `app/src/main/java/com/izavo/app/ui/ExpenseTrackerApp.kt`
- `app/src/main/java/com/izavo/app/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/izavo/app/ui/home/HomeV3Screen.kt`
- `app/src/main/java/com/izavo/app/ui/design/ExpenseComponents.kt`
- `app/src/main/java/com/izavo/app/ui/design/ExpenseDesign.kt`
- `app/src/main/java/com/izavo/app/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/izavo/app/ui/history/HistoryComponents.kt`
- `app/src/main/java/com/izavo/app/ui/statistics/StatisticsScreen.kt`
- `app/src/main/java/com/izavo/app/ui/statistics/StatisticsCharts.kt`
- `app/src/main/java/com/izavo/app/ui/detail/ExpenseDetailSheetV2.kt`
- `app/src/main/java/com/izavo/app/ui/quickadd/ExpenseFormSheet.kt`
- `app/src/main/java/com/izavo/app/ui/importing/SmartImportReviewScreen.kt`
- `app/src/androidTest/java/com/izavo/app/ui/statistics/StatisticsScreenTest.kt`
