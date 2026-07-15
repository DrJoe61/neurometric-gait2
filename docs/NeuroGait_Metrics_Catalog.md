# NeuroGait — Gait Metrics Catalog

**One sacral IMU6 (accel + gyro, 104 Hz) at S1–S2 midline.**
What we can measure, what each signal means for left vs right leg and the pelvis,
and how honest each number is. Ordered by what to build first.

Sensor axes (from the Sacrum guide):
`+Z = superior (up)` · `+Y = left` · `+X = anterior`.
Gyro is angular velocity about each axis, integrated per stride for range-of-motion.

---

## Tier 1 — Direct from the sacral IMU (build now, from your CSV)

These need nothing but the sensor. Your jog CSV already holds the raw data for all of them.

| # | Metric | Signal used | What it means | Honesty |
|---|--------|-------------|---------------|---------|
| 1 | **Cadence (real)** | accZ footstrike peaks | Steps/min. Needs double-peak fix (see below). | ✅ Accurate once tuned |
| 2 | **Step time** (per step, ms) | interval between footstrikes | Rhythm; feeds L/R symmetry | ✅ |
| 3 | **Stride time** (ms) | two consecutive steps | One full gait cycle | ✅ |
| 4 | **Peak vertical impact** (g) | accZ / resultant peak per step | Landing shock per foot | ✅ |
| 5 | **Loading rate** (g/s) | slope of accel rise to peak | *How fast* shock arrives — a stronger injury marker than peak alone | ✅ |
| 6 | **Vertical oscillation** (cm) | double-integrate accZ per stride | COM "bounce"; efficiency | ⚠️ ±0.5 cm, drift-corrected per stride |
| 7 | **Ground contact time** (ms/step) | footstrike → toe-off features in accel | Time on road each step | ⚠️ Sacrum estimate, ±20–30 ms vs insole |
| 8 | **Flight time / duty factor** | stride time − GCT | Running efficiency | ⚠️ inherits GCT error |

---

## Tier 2 — Pelvis (gyroscope → 3 planes)

Integrate each gyro axis over a stride to get **range of motion** in degrees. This is the
part a sacral sensor does *better* than a foot pod — it sits on the pelvis.

| # | Metric | Gyro axis | Plane / meaning | Clinical read |
|---|--------|-----------|-----------------|---------------|
| 9 | **Pelvic tilt** (ant/post) | about **Y** (ML axis) | Sagittal — forward/back rock | Core control, over-striding |
| 10 | **Pelvic obliquity / drop** | about **X** (AP axis) | Frontal — hip drops toward swing leg (Trendelenburg) | **Hip-abductor weakness; the #1 L/R red flag** |
| 11 | **Pelvic rotation** | about **Z** (vertical) | Transverse — axial twist, alternates each step | Trunk rotation, stride reach |

> ⚠️ **Code label note:** the current analyzer maps GyrX→roll, GyrY→pitch. For clean
> anatomical meaning, obliquity (the L/R marker) should come from rotation about the
> **anterior (X)** axis and tilt from the **left (Y)** axis. Worth aligning before we score it.

---

## Tier 3 — Left leg vs right leg (single-sensor method)

A midline sensor can't *see* two legs directly, but footfalls **alternate** L-R-L-R,
so we split them:

1. Detect every footstrike (Tier 1 #1).
2. Odd strikes = one foot, even strikes = the other.
3. **Label** which is left vs right from the **frontal-plane angular velocity (obliquity)
   sign** at each strike — the pelvis drops toward the *swing* side, so the polarity at
   stance tells us the stance foot. (Backup: user starts on a known foot.)
4. Compare the two groups.

| # | Metric | Meaning | Honesty |
|---|--------|---------|---------|
| 12 | **L/R impact asymmetry** | Does one leg land harder? | ⚠️ Screening-grade |
| 13 | **L/R step-time asymmetry** | Limp / favoring a side | ⚠️ Screening-grade |
| 14 | **L/R ground-contact asymmetry** | One foot lingers longer | ⚠️ Screening-grade |
| 15 | **L/R pelvic-drop asymmetry** | Weak side abductors | ⚠️ Screening-grade |
| 16 | **Symmetry Index (composite)** | `SI = \|L−R\| / (½(L+R)) × 100%` per metric; <5% good, >10% flag | ⚠️ Screening-grade |
| 17 | **Step/stride regularity** | Autocorrelation of accel — consistency & symmetry in one number | ✅ Robust |

> Honesty: single midline sensor gives **asymmetry screening**, not gold-standard
> per-limb kinetics. Great for tracking *change over time* and flagging a side; not a
> force-plate substitute.

---

## Tier 4 — Distance & speed (needs GPS fusion — the A2 build)

Stride *distance* cannot come from the IMU alone. Your A2 spec nails it:

```
stride length (m) = GPS speed (m/s) ÷ (cadence_spm ÷ 2 ÷ 60)
```

| # | Metric | Source | Honesty |
|---|--------|--------|---------|
| 18 | **Real stride length** (m) | GPS speed ÷ cadence | ✅ when GPS accuracy <5 m; ⚠️ 5–10 m; ❌ >10 m |
| 19 | **Speed / pace** (min/km) | GPS | ✅ outdoor |
| 20 | **Distance** (km) | GPS | ✅ outdoor |
| 21 | **Stride-length variability** | SD per 30 s | Injury-risk marker |
| 22 | **Fatigue index** | stride shortening in final segment | Training-load marker |
| 23 | **Impact in Newtons** | `peak_g × body_mass × 9.81` | ⚠️ approximate, needs your mass |

> Treadmill: GPS reads zero → user types treadmill speed; everything else still works.

---

## The graphs (session view)

Built from the per-session CSV:

1. **Impact-per-step bar chart**, each bar colored L (blue) / R (orange) — asymmetry at a glance.
2. **Cadence over time** — warm-up drift, fatigue fade.
3. **Pelvic obliquity L vs R** — mirrored bars; the abductor-weakness picture.
4. **Ground-contact time per step** — L vs R.
5. **Symmetry dashboard** — the SI numbers with a green/amber/red band.
6. **Stride length over distance** *(A2, GPS)* — holds steady or shortens with fatigue.
7. **Route heat-map colored by impact** *(A2, GPS)* — where you land hardest.

---

## Two things before the next jog

**A. Fix cadence over-count (priority).** Your last jog read 233 spm avg / 354 peak —
physiologically impossible. At the sacrum, *running* produces a **double peak per stance**
(heel-strike + push-off), so the detector counts each footfall ~twice. Fix = raise the
refractory window to ~280–300 ms and lock onto the dominant peak (or bandpass 0.5–3 Hz).
That alone makes cadence, step time, and everything derived from them real.

**B. Decide what we store for graphs.** The graphs need the time-series, which the app
already writes to the **local CSV**. Options: (a) analyze the CSV on the phone and upload
richer per-session aggregates (L/R values, GCT, pelvic ROM) to Supabase, or
(b) upload the full CSV to Supabase Storage for offline processing. (a) is lighter and
keeps the suite tables clean.

---

*Sources: NeuroGait A2 GPS+IMU Sensor Fusion spec; Movesense Sacrum Biomechanics guide
(axis orientation, analyzer); gait literature cited therein (Kavanagh & Menz 2008 sacral
accelerometry; Milner et al. 2006 loading rate; Heiderscheit et al. 2011 cadence).*
