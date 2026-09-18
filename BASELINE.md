# IZAVO — Project Baseline

**Recorded:** 18 September 2026  
**Checkpoint:** initial GitHub commit `e6af836` on `main`

This note records the known-good project state at the initial GitHub checkpoint. It is a regression and reference point for future development, not a claim that the app is production-ready.

## Product

IZAVO is a native Android personal expense tracker built with Kotlin and Jetpack Compose (`com.izavo.app`). It is designed for offline, local-first financial tracking. The core goal is fast everyday expense capture with useful historical and statistical context, without fabricating financial meaning.

Quick Add requires only a valid amount. Each expense stores its own currency; totals remain separate, with no exchange-rate conversion. The app includes Home, History, Statistics, Settings, onboarding, CSV export, feedback, a Quick Settings **Add Expense** tile, and historical BML CSV import.

## Core data architecture

Add/Edit/Delete flows go from Compose UI through `ExpenseViewModel`, `ExpenseRepository`, and Room/SQLite; Room `Flow` queries refresh Compose screens. `ExpenseEntity` stores `currencyCode` per expense. `ExpenseDatabase` is **version 4** with explicit 1→2→3→4 migrations.

Quick Add's Undo uses the inserted entity returned with its actual Room ID, then deletes that same entity; it does not undo by amount, timestamp, or “last row.” Edit updates the existing row.

## Smart Import architecture and semantic safety

The current path is BML CSV parsing → canonical transaction representation → V4 semantic intelligence → Review Necessity Model (RNM) → adaptive review → transactional Room persistence. The bank ledger retains source rows and fingerprints. Merchant rules and approved expense rows are persisted with the import batch in one Room transaction; a duplicate discovered at persistence time fails and rolls back that transaction.

**How money moved is not necessarily what the money was for.** The persisted review decisions are `EXPENSE`, `INCOME`, `MONEY_MOVEMENT`, `REFUND`, `UNKNOWN`, and `IGNORE`. The normalized transaction model also distinguishes bank-rail types such as incoming/outgoing transfer, cash movement, debt payment, and reversal. Only rows resolved as `EXPENSE` create `ExpenseEntity` records. Unknown or deferred rows remain ledger-only and are not silently counted as spending.

## Smart Import V4 safety baseline

The repository contains the exact **synthetic** Level 1, 2, and 3 torture fixtures and ground truth in `IZAVO_BML_TORTURE_V2/`. The JVM benchmark report records:

| Fixture | Source rows | Unique rows | Exact duplicates | Automatic semantic precision | Catastrophic semantic errors |
| --- | ---: | ---: | ---: | ---: | ---: |
| Level 1 | 100 | 100 | 0 | 100% | 0 |
| Level 2 | 500 | 498 | 2 | 100% | 0 |
| Level 3 | 1,200 | 1,198 | 2 | 100% | 0 |

These precision values apply to the **automatic decisions on these synthetic fixtures**, not to arbitrary real statements. Fingerprint checks protect overlaps and re-imports; import persistence is transactional, including duplicate-race rollback. V4 semantic safety is considered frozen unless a deliberate future change receives equivalent regression certification.

## Review Necessity Model baseline

V4 asks, “What does this transaction most likely mean?” RNM asks, “Does the user actually need to answer this now?” Its dispositions are `ALREADY_RESOLVED`, `ASK_NOW`, and `DEFER`; RNM never invents Expense, Income, Movement, or Refund semantics.

The current policy generally defers unresolved incoming rows. It evaluates unresolved outgoing rows **independently per currency**: positive buckets of at most three ask all; larger buckets prioritize high-value rows until at least **96%** of plausible unresolved outgoing value is covered. No cross-currency aggregation or conversion is used.

### RNM certification

The stored `ReviewNecessityCertificationTest` output for the exact synthetic fixtures reports:

| Fixture | V4 review rows | RNM ASK | RNM DEFER | Final adaptive decisions | Expense-count capture | Expense-value capture | Semantic errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Level 1 | 51 | 24 | 27 | 24 | 97.62% | 99.72% | 0 |
| Level 2 | 260 | 121 | 139 | 120 | 95.17% | 98.79% | 0 |
| Level 3 | 630 | 293 | 337 | 292 | 95.35% | 98.88% | 0 |

Capture measures fixture ground-truth expenses either automatically resolved or sent to review. Deferred expenses are therefore a measured completeness trade-off, not silently inferred spending.

## Known product limitation

The remaining Smart Import review burden is a **known product limitation, not an unresolved semantic safety bug**. A BML statement may show an outgoing person/P2P payment without showing whether it was a purchase, repayment, transfer, or other money movement. Lowering review counts only through inference would either defer more genuine expenses and reduce historical completeness, or guess meaning unsupported by the statement.

The certified full-review burdens after RNM and adaptive review are **24**, **120**, and **292** final decisions for Levels 1–3. Future UX could intentionally let users finish an import without completing every optional review, leaving unresolved transactions `UNKNOWN`; this is a direction, not a claim that every such flow is already implemented.

## Functional baseline

The checked-in implementation covers expense Add/View/Edit/Delete and exact inserted-ID Undo; Home, History with search and filters, Statistics, Settings, onboarding, and multi-currency display; Quick Settings entry, CSV export, and feedback; BML import with duplicate fingerprints, overlap/re-import protection, and ledger-only non-expenses. These are source/test-supported behaviors, not a substitute for physical-device acceptance testing.

## Design baseline

Keep the Android-native presentation's restrained Apple-like hierarchy and precision: strong typography and whitespace, large financial numbers, near-white and dark blue-black surfaces, restrained cyan/cool-blue accent, selective translucency, minimal cards and pills, atmospheric Home treatment, floating/system-like navigation, and subtle purposeful motion and haptics. Avoid generic Material dashboards, neon/gaming/crypto styling, and rainbow analytics. Do not casually rewrite the existing Home or onboarding visual structure during unrelated engineering work.

## Statistics baseline

Statistics calculates from positive `ExpenseEntity` amounts within the selected currency and period. Currencies are never converted or combined; linked import rows provide merchant metadata, not extra spending. Categories use the continuous composition-bar presentation rather than the retired artifact-prone donut view. The presentation aims to tell a financial story rather than form a card-heavy dashboard.

## Security and privacy baseline

Financial data is local/offline by current design; there is no exchange-rate API. Do not commit real bank statements, signing keys, or secrets. Only synthetic test fixtures belong in Git. The repository is currently intended to remain private. Keep generated APK/AAB and build output outside Git. These constraints are not an absolute security certification.

## Current test baseline

The existing debug JVM XML results report **232 passed, 0 failed** across 20 test classes; 171 of those tests are in the import package. The stored exact-fixture benchmark and RNM certification outputs support the figures above. Kotlin compilation and debug APK assembly passed at the initial engineering checkpoint; the debug APK exists in ignored build output. Android instrumentation source compilation was reported as passed at that checkpoint, while device instrumentation execution was **not** run in that certification. This documentation-only commit did not rerun the build or tests.

## Regression Invariants

- Amount remains the only mandatory Quick Add field; CRUD remains functional, and Undo deletes the exact inserted expense.
- Currencies are never silently converted or combined.
- V4 semantic safety must not be accidentally loosened; RNM must not create semantic meanings, and `UNKNOWN` must never silently become spending.
- Incoming and outgoing person contexts must not leak learned semantics across direction.
- Duplicate fingerprints, overlap/re-import protections, and transactional import persistence remain intact.
- Deferred or skipped rows must not create unwanted `ExpenseEntity` records.
- Real financial data and secrets must never enter Git.
- Onboarding/status-bar fixes and Home's visual hierarchy/atmosphere must not regress during unrelated work.

## Baseline Status

This is the initial GitHub baseline/checkpoint for future development. **Engineering baseline:** known-good according to the verified tests and checkpoint evidence above. **Known product issue:** historical BML imports may still require too many human decisions when the statement itself lacks semantic information. Next product work should make unavoidable uncertainty painless rather than weaken semantic safety.
