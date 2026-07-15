# NeuroGait — Fix List & Build Plan

**Compiled:** 2026-07-14 · 21:40 EDT
**For build session:** 2026-07-15 (build to phone + debug pass)
**Package:** `com.proneurolight.neurometric.pace`
**Refs:** NeuroGait_Metrics_Catalog.md · NeuroGait_DerivedMetrics_Reference06.docx

---

## A. Already done (baseline going into tomorrow)

- Scan screen: big **SCAN + CONNECT MOVESENSE** button, live status line, auto-connect to first Movesense, **main-thread fix** (the old background-thread list update was the "hang").
- Google sign-in gate + **silent auto-select** for returning users.
- **STOP → "Save and exit?" YES / NO.** YES = upload + close app · NO = discard + back to Scan.
- **No phone CSV.** Raw stream → new `pace_session_raw` table (one row/run, every sample). Summary (6 numbers) → `suite_metrics`.
- Supabase `pace_session_raw` table live (RLS locked to your user_id).
- No new dependencies since last build → **Run, no Gradle sync needed.**

---

## B. Tomorrow — ordered

1. **Build to phone + install.** Just Run ▶. (New app icon; old one can be deleted.)
2. **Sign in once, take a jog, STOP → YES.**
3. **Debug pass** (checklist in section C) — watch logcat live during the run.
4. **Verify data landed** — I query Supabase for both tables (summary + full raw row) and confirm sample count.
5. **THE big fix: cadence over-count.** Step detector double-counts (233/354/1092 spm seen). Fix = refractory window ~280–300 ms + lock onto the dominant footstrike peak (bandpass 0.5–3 Hz option). Makes cadence, step interval, and everything derived from them trustworthy.
6. **Reconcile gyro axis labels** — catalog says obliquity about X; reference doc says `GyrY = pelvic drop`. Pick one mapping before we score the pelvis, so L/R hip-drop is correct.
7. **Confirm sign-in persists** across a normal relaunch (not a reinstall).

---

## C. Debug checklist (run this during the jog)

Logcat filter: `package:com.proneurolight.neurometric.pace`

| Stage | Look for | Good | Bad |
|---|---|---|---|
| Sign-in | tag `SupabaseIdentity` | no re-prompt after first login | prompts every launch → session not saving |
| Connect | tag `StreamActivity` | `onConnectionComplete … serial=…` | `MDS connection error` |
| Streaming | tag `IMU` | `Batch N samples \| Acc(...) Gyr(...)` repeating | lines stop = stall |
| Freeze watch | on-screen status | stays `● Streaming (live)` | `⚠ Signal lost` + `IMU` batch lines stop → the BLE stall, still undiagnosed |
| Save (YES) | tag `SuiteSync` | `Pushed 6 pace metrics` **and** `Pushed raw session (N samples)` | `Push failed` / `Raw push failed` / `Not signed in` |

If it freezes: note the **timestamp of the last `IMU` batch line** and whether any `MDS`/`BleManager` disconnect or timeout appears right after — that's what pins the stall.

---

## D. Known open items / roadmap

- **Freeze mid-session** (BLE stall, not rotation) — still undiagnosed; the checklist above is designed to catch it.
- **Raw payload size** — a long run (~20 min ≈ 100k+ samples) uploads as one large text blob; fine for now, can downsample later if it drags.
- **A2 — GPS fusion:** real stride length = `GPS_speed ÷ (cadence/2/60)`, real pace/distance, stride variability, fatigue index.
- **A2 — Madgwick AHRS** to kill gyro drift (beta 0.05–0.1).
- **A2/A3 — pelvis + L/R** scoring (rotation °/step, lateral tilt, sway index, symmetry), then **composite 0–100 scores** (Efficiency, Impact, Stability, Variability, Endurance, Warmup).
- **A3 — Force Portrait** (AccX vs AccZ cloud; Landing/Stabilizing/Launching/Flying phases).
- **Graphs** from `pace_session_raw`: impact-per-step L/R, cadence curve, pelvic-drop symmetry, ground-contact per step.

---

*Next action tomorrow: build + install → jog → STOP → YES → ping me for the live Supabase read + debug.*
