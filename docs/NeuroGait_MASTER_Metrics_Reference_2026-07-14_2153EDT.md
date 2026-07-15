# NeuroGait — Master Metrics Reference

**App:** NeuroGait (NeuroMetric PACE) · package `com.proneurolight.neurometric.pace`
**Sensor:** Movesense MD · **Placement:** Sacrum (S1–S2) · **Rate:** 104 Hz (IMU6)
**Compiled:** 2026-07-14 · 21:53 EDT · single source of truth
**Merged from:**
- `NeuroGait_DerivedMetrics_Reference06.docx` (May 2026) → tagged **[R06]**
- `NeuroGait_Metrics_Catalog.md` (2026-07-14) → tagged **[CAT]**
- Present in both → **[BOTH]**

> Provenance tags show where each block originated. R06 is the formal master
> reference; CAT added the single-sensor L/R method, the graph set, and the
> cadence-fix / storage notes.

---

## Raw hardware outputs (Movesense MD) — [R06]

| Signal | Range / rate | Notes |
|---|---|---|
| Acceleration X / Y / Z | ±8 g, up to 1666 Hz | we sample 104 Hz |
| Rotation X / Y / Z (gyro) | up to 1666 Hz | °/s |
| Magnetic field X / Y / Z | ~50 Hz | unused in A1 |
| GPS | not in hardware | comes from phone (A2) |
| HR / ECG | none from sacrum | belongs to DFa1 chest strap |

---

## Sacrum axis reference — [R06] (⚠ conflict to resolve, see note)

| Axis | Direction | Biomechanical meaning |
|---|---|---|
| AccZ | Superior | Vertical oscillation — **primary step-detection axis** |
| AccY | Left lateral | Mediolateral accel — pelvic sway |
| AccX | Anterior | Sagittal — fore-aft propulsion |
| GyrZ | Yaw | Pelvic rotation (transverse) |
| GyrY | Pitch | **Pelvic drop — Trendelenburg / hip stability** |
| GyrX | Roll | Lumbar flexion/extension rhythm |

> **⚠ Axis conflict [BOTH]:** R06 puts pelvic drop on **GyrY (pitch)**; CAT put
> obliquity/drop on rotation about the **anterior X** axis. Same motion, different
> label. **Lock R06's mapping (GyrY = pelvic drop)** unless a mounting test says
> otherwise — the code analyzer must match, or L/R hip-drop asymmetry inverts.

---

## Group 1 — Cadence & pace — [BOTH]

Steps detected on filtered **AccZ** (double-peak per stance at running speed).

| Metric | Method | Reliability | Phase |
|---|---|---|---|
| Cadence (spm) | `60000 / avg_step_interval_ms` | ★★★★★ | A1 now |
| Step interval (ms) | `ts[n] − ts[n−1]` at each peak | ★★★★★ | A1 now |
| Rolling cadence (10 s) | mean interval, last 10 s | ★★★★★ | A1 now |
| Session avg cadence | mean interval, whole session | ★★★★★ | A1 now |
| Cadence variability | SD of step intervals (ms) | ★★★★★ | A1 now |
| Estimated speed (m/s) | `cadence × stride_model / 60` | ★★★☆☆ | A1 (no GPS) |
| Estimated pace (min/km) | from est. speed | ★★★☆☆ | A1 (no GPS) |

**A1 stride fallback (no GPS) — [R06]:** `stride_m = 0.0027 × cadence_spm + 0.43`
(Grieve & Gear, ±15%); `speed_ms = (cadence/2) × stride_m / 60`. Replaced by GPS in A2.

---

## Group 2 — Impact force — [BOTH]

`resultant_g = sqrt(accX² + accY² + accZ²)` · `impact_N = peak_g × mass_kg × 9.81`

| Metric | Method | Reliability | Phase |
|---|---|---|---|
| Peak impact / step (g) | max resultant during loading | ★★★★★ | A1 now |
| Session peak (g) | max across session | ★★★★★ | A1 now |
| Avg impact / step (g) | mean peak across steps | ★★★★★ | A1 now |
| Impact force (N) | `peak_g × mass × 9.81` | ★★★★☆ | A1 now |
| **Loading rate (g/s)** | slope of resultant rise to peak | ★★★★☆ | A1/A2 |
| Impact drift | regression of peak_g vs time | ★★★★☆ | A2 |
| Foot-strike class | heel/mid/fore from impact shape | ★★★☆☆ | A2 |

**Reference bands (sacrum) — [R06]:** walk 1.2–1.8 g · easy run 2.0–2.8 · tempo
2.5–3.5 · hard 3.0–4.5 · forefoot 1.5–2.5 (lower peak, higher loading rate).

---

## Group 3 — Gyroscope & pelvic movement — [BOTH]

| Metric | Method | Reliability | Phase |
|---|---|---|---|
| Pelvic rotation (°/step) | GyrZ integrated per step | ★★★★☆ | A1/A2 |
| Lateral pelvic tilt (°) | GyrY peak-to-peak per step | ★★★★☆ | A1/A2 |
| Trunk forward lean (°) | GyrX integrated | ★★★★☆ | A2 |
| Sway index | RMS of AccY across session | ★★★★☆ | A1/A2 |
| L/R symmetry (%) | L vs R pelvic-motion peaks | ★★★☆☆ | A2 |
| Vertical oscillation (cm) | double-integrate AccZ | ★★★☆☆ | A2 |
| Euler R/P/Y | integrate GyrX/Y/Z (drifts) | ★★★☆☆ | A1 basic |
| Madgwick AHRS | Acc+Gyr fusion, drift-free | ★★★★★ | A2 upgrade |

---

## Group 3b — Left leg vs right leg (single-sensor method) — [CAT]

A midline sacral sensor infers sides from **alternating footfalls**:
1. Detect every footstrike (Group 1).
2. Odd strikes = one foot, even = the other.
3. Label L/R from the **frontal-plane angular-velocity sign** at stance (pelvis
   drops toward the swing side).
4. Compare groups.

| Metric | Meaning | Honesty |
|---|---|---|
| L/R impact asymmetry | one leg lands harder | screening |
| L/R step-time asymmetry | limp / favoring | screening |
| L/R ground-contact asymmetry | one foot lingers | screening |
| L/R pelvic-drop asymmetry | weak-side abductors | screening |
| Symmetry Index | `\|L−R\| / (½(L+R)) × 100%`; <5% good, >10% flag | screening |
| Step/stride regularity | autocorrelation of accel | ★ robust |

> Single midline sensor = **asymmetry screening**, not per-limb force plates.

---

## Group 4 — GPS fusion (A2) — [BOTH]

**Key equation:** `stride_length_m = GPS_speed_ms ÷ (cadence_spm ÷ 2 ÷ 60)`

| Metric | Method | Requires |
|---|---|---|
| Real stride length (m) | key equation | GPS + cadence |
| Real speed / pace | phone GPS | GPS |
| Stride-length variability | SD per 30 s | GPS + cadence |
| **Ground contact time (ms)** | impact-signal duration above threshold / step | IMU only (★★★☆☆) |
| Running economy trend | stride drift over time | GPS + cadence |
| Fatigue index | stride shortening final 20% | GPS + cadence |
| GPS route / impact heat map | lat/lon colored by peak_g | GPS + impact |

**GPS reliability rules — [R06]:** <5 m green (confirmed) · 5–10 m amber
(estimated) · >10 m red (hide) · GPS=0 treadmill (manual speed).

---

## Group 5 — Composite session scores (0–100, A2+) — [R06]

| Score | Measures | Inputs |
|---|---|---|
| Efficiency | cadence consistency + low vertical waste | cadence variability, vert. osc., interval consistency |
| Impact | penalizes high peak g + loading rate | peak g, loading rate, impact drift |
| Stability | sway, rotation symmetry, lean | sway index, rotation °/step, L/R symmetry |
| Variability | stride-to-stride consistency | interval SD, stride variability |
| Endurance | drift over time (fatigue) | cadence/impact/stride trend |
| Warmup | quality of first 2 min | cadence ramp, early impact |
| **Overall** | weighted composite | all above |

*Benchmark — [R06]:* AletheiaRun (aletheia.run), same sacrum placement, shows
these as a rainbow-arc gauge + Force Portrait. Their UI = reference, not our target style.

---

## Group 6 — Force Portrait (A3) — [R06]

Plot acceleration axes against each other over many strides → biomechanical fingerprint.

| View | Axes | Shows |
|---|---|---|
| Side | AccX vs AccZ | sagittal — propulsion vs braking |
| Rear | AccY vs AccZ | frontal — sway + vertical osc. |
| Top | AccX vs AccY | transverse — rotation pattern |

Phases: **Landing** (red) · **Stabilizing** (amber) · **Launching** (green) · **Flying** (blue).

---

## Output graphs from `pace_session_raw` — [CAT]

1. Impact-per-step bars, colored L/R · 2. Cadence over time · 3. Pelvic-drop L vs R ·
4. Ground-contact per step · 5. Symmetry dashboard · 6. Stride length over distance (A2/GPS) ·
7. Route heat-map by impact (A2/GPS).

---

## Data storage (current) — [CAT]

**No phone CSV.** On STOP → YES:
- Full raw stream → Supabase `pace_session_raw` (one row/run: `timestamp_ms, accX/Y/Z_g,
  gyrX/Y/Z, step_count, cadence_spm, peak_impact_g` for every sample).
- Summary → `suite_metrics` (6 rows, `app_source = neurometric_pace`).

*Legacy A1/A2 CSV column plans from R06 retained for the eventual GPS columns:*
`gps_lat, gps_lon, gps_speed_ms, gps_accuracy_m, stride_length_m, cadence_10s_rolling,
impact_newtons, loading_rate, pelvic_rotation_deg, pelvic_sway_deg, vertical_oscillation_cm,
session_mode`.

---

## Known fix — cadence over-count — [CAT]

Running produces a **double AccZ peak per stance** → detector counts each foot ~twice
(observed 233/354/1092 spm). Fix: refractory ~280–300 ms + lock dominant peak
(bandpass 0.5–3 Hz). Blocks trustworthy cadence/stride/symmetry until fixed.

---

## Research references — [R06]

Kavanagh & Menz 2008 (sacrum best single placement; Z-axis steps) · Heiderscheit 2011
(cadence 170–180 cuts impact) · Meardon 2011 (stride variability ↔ stress fracture) ·
Riley 2008 (treadmill stride 4–8% shorter) · Milner 2006 (loading rate ↔ injury) ·
Grieve & Gear 1966 (stride estimate) · Madgwick 2010 (AHRS drift correction).

---

*NeuroGait Master Metrics Reference · 2026-07-14 21:53 EDT · BrainSherpa / NeuroMetric ·
supersedes NeuroGait_DerivedMetrics_Reference06.docx + NeuroGait_Metrics_Catalog.md.*
