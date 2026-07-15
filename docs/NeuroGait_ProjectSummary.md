# NeuroGait — Project Summary & Design Decisions
**Document created:** May 2026  
**Status:** Pre-build — A1 Prototype phase  
**Author:** Collaborative planning session (User + Claude)

---

## 1. Project Overview

**NeuroGait** is an Android application that connects to a **Movesense MD sensor** strapped at the **sacrum** and streams biomechanical running data in real time. It saves session data as CSV files and displays session history for review and sharing.

This is part of the broader **BrainSherpa / NeuroMetric** ecosystem, which already includes a working HRV/DFa1 app. NeuroGait will eventually integrate with that app, but is being built as a standalone prototype first.

**Competitive reference:** AletheiaRun (iOS only, proprietary sensor, subscription model). NeuroGait targets Android, uses the open Movesense platform, and will offer raw CSV export — features AletheiaRun does not provide.

---

## 2. Hardware

### Movesense MD Sensor
- **Placement:** Sacrum (S1-S2 level, posterior midline, between PSIS landmarks)
- **Model:** Movesense MD (includes accelerometer, gyroscope, magnetometer — **no GPS**)
- **Note:** No ECG/HR from sacrum placement — HR requires chest contact
- **Connection:** Bluetooth LE → Android phone via MDS library

### Axis Orientation at Sacrum
```
+Z = Superior (cranial) ← primary step detection axis
+Y = Left (mediolateral) ← pelvic obliquity / sway
+X = Anterior ← sagittal plane motion

GyrX = Roll  (lumbar flexion/extension rhythm)
GyrY = Pitch (pelvic drop, Trendelenburg)
GyrZ = Yaw   (pelvic rotation during gait)
```

### Recommended Settings for Running
- Endpoint: `Meas/IMU6/104` (synchronized Acc + Gyro at 104 Hz)
- Accelerometer range: **±8g** (default ±2g clips on hard heel-strike)
- Sample rate: **104 Hz** for running, 52 Hz acceptable for walking

---

## 3. Target Device

- **Phone:** Samsung Galaxy A26 5G
- **Android version:** 16 (One UI 8.0)
- **Android Studio:** Latest (user to confirm version on next session)

---

## 4. App Identity

| Field | Value |
|---|---|
| App name | **NeuroGait** |
| GitHub org | brainsherpa (to confirm exact username) |
| Package name | `com.brainsherpa.neurogait` OR `xyz.neurogait.app` (pending domain check) |
| Repo name | `neurogait` |
| Repo visibility | Private (initially) |
| Folder path | `Documents/BrainSherpa/NeuroGait/` |

**Pending:** User to confirm GitHub exact username and whether `neurogait.xyz` domain is owned.

---

## 5. Build Strategy — Prototype First

### Why A1 First
AletheiaRun's own version history shows they started simple and added complexity over 12+ months. We follow the same logic:

- See real data from YOUR body before writing algorithms
- Identify which axis is cleanest for step detection at your sacrum
- Calibrate impact g values at your actual running pace
- Avoid building algorithms on wrong assumptions

### A1 Prototype Scope (Current Build Target)
```
SCREEN 1 — Connect
  • Scan for Movesense devices
  • Tap to connect
  • Show device serial when connected

SCREEN 2 — Live Stream
  • Raw values updating live:
    AccX / AccY / AccZ (g)
    GyrX / GyrY / GyrZ (°/s)
  • Derived values:
    Step count
    Elapsed time
    Cadence (steps/min, rolling 10s)
    Peak impact (g, per session)
  • START / STOP button

SCREEN 3 — Post Session
  • Session saved confirmation
  • CSV auto-saved to phone storage
  • Share button (user choice — no auto-email)

SCREEN 4 — Session History
  • List by date/time (matching existing NeuroMetric style)
  • 👁 View summary
  • ↓ Share/download CSV anywhere (user's choice of destination)
  • 🗑 Delete
```

### A2 and Beyond (Backlog)
- Force Portrait scatter plots (Side / Top / Rear views — inspired by AletheiaRun)
- Scored metrics dashboard (Efficiency, Impact, Stability, Variability, Endurance)
- Gait phase detection (Landing / Stabilizing / Launching / Flying)
- Madgwick AHRS filter replacing simple Euler gyro integration
- Integration with NeuroMetric DFa1/HRV app
- Wear OS watch face (cadence + impact + stability — 3 numbers)
- Coach tab / exercise recommendations (AI layer via Claude API)

---

## 6. Metrics — Full Capability Map

### What Movesense MD at the Sacrum Delivers

#### Raw Hardware Outputs
| Signal | Source | Max Rate |
|---|---|---|
| Acceleration X/Y/Z | Accelerometer | 1666 Hz |
| Rotation X/Y/Z | Gyroscope | 1666 Hz |
| Magnetic field X/Y/Z | Magnetometer | ~50 Hz |
| Temperature | Thermometer | slow |
| **GPS** | ❌ Not available | — |

#### Derived Metrics — Cadence Group
| Metric | Method | Reliability |
|---|---|---|
| Cadence (steps/min) | Time between impact peaks on Z-axis | ★★★★★ |
| Step interval (ms) | Direct from timestamps | ★★★★★ |
| Cadence variability | Std deviation of step intervals | ★★★★★ |
| Rolling cadence (10s) | 10s sliding window average | ★★★★★ |
| Session avg cadence | Full session mean | ★★★★★ |
| Estimated speed (m/s) | Cadence × stride length (from leg length) | ★★★☆☆ |
| Estimated pace (min/km) | Derived from speed estimate | ★★★☆☆ |
| Estimated distance | Accumulated stride estimates | ★★☆☆☆ |

#### Derived Metrics — Impact Group
| Metric | Method | Reliability |
|---|---|---|
| Peak impact per step (g) | Resultant acceleration max | ★★★★★ |
| Impact force (Newtons) | peak_g × body_mass × 9.81 | ★★★★☆ |
| Loading rate | Rise rate of g per step | ★★★★☆ |
| Session peak impact | Max g across whole session | ★★★★★ |
| Avg impact per step | Session mean | ★★★★★ |
| Foot strike pattern | Heel/midfoot/forefoot classification | ★★★☆☆ |

#### Derived Metrics — Gyroscope / Movement Group
| Metric | Method | Reliability |
|---|---|---|
| Pelvic rotation °/step | GyrZ integrated per step | ★★★★☆ |
| Pelvic lateral tilt | GyrY — Trendelenburg sway | ★★★★☆ |
| Trunk forward lean | GyrX / pitch angle | ★★★★☆ |
| Sway index | Lateral movement magnitude | ★★★★☆ |
| Symmetry score | Left/right pelvic motion balance | ★★★☆☆ |

#### Scored Summary Metrics (A2+)
- **Efficiency** — cadence consistency + low vertical oscillation
- **Impact score** — penalises high peak g and high loading rate
- **Stability** — pelvic sway and rotation symmetry
- **Variability** — stride-to-stride consistency
- **Endurance index** — cadence/impact drift over time (fatigue marker)
- **Warmup detection** — first 2 min flagged separately

---

## 7. User Profile Inputs

These are entered once and persist across all sessions:

| Field | Purpose |
|---|---|
| Name | Session labelling |
| Height (cm) | Normalisation |
| Weight (kg) | Newton force calculation: F = peak_g × kg × 9.81 |
| Bellybutton-to-ground (cm) | Leg length proxy for stride estimation |

**Note:** Bellybutton-to-ground is a validated field measurement for leg length in gait research (Cavanagh model). The actual sacrum (S1) sits ~5-8cm posterior and slightly above navel level, but this measurement is the practical standard.

---

## 8. CSV Output Format

### A1 CSV (prototype — includes GPS columns from day one)
```
timestamp_ms, accX, accY, accZ, gyrX, gyrY, gyrZ,
step_count, cadence_spm, peak_impact_g,
gps_lat, gps_lon, gps_speed_ms, gps_accuracy_m,
stride_length_m
```

### A2 CSV (full — see NeuroGait_A2_GPS_SensorFusion.md)
```
+ cadence_10s_rolling, impact_newtons, loading_rate,
+ stride_length_reliable, pelvic_rotation_deg,
+ pelvic_sway_deg, session_mode
```

- One row per IMU sample (104 rows per second)
- Named by session date/time: `NeuroGait_2026-05-03_0923.csv`
- Saved to phone's external files directory
- Shared via Android share sheet (email, Drive, WhatsApp, etc.) — user chooses destination
- `stride_length_m` left blank when GPS accuracy > 10m

### GPS + Cadence Fusion (KEY INSIGHT — added May 2026)
Phone GPS speed ÷ Movesense cadence = **real stride length** — no estimation needed.
See full design doc: `NeuroGait_A2_GPS_SensorFusion.md`

---

## 9. UI Design Direction

### Theme
- Dark background (black / near-black)
- Accent colour: TBD (open to new palette beyond the existing blue)
- Inspired by: existing NeuroMetric session history screen + AletheiaRun score dashboard

### Live Session Screen — Option C (Hybrid)
```
TOP HALF — Big numbers
┌────────────────────────────────────┐
│  CADENCE      IMPACT       TIME    │
│   172          2.8g       04:23   │
│   spm          peak      elapsed  │
│                                    │
│  Rolling cadence (10s): 174 spm   │
│  Est. pace: 5:12 min/km           │
└────────────────────────────────────┘

BOTTOM HALF — Live mini score gauges
┌────────────────────────────────────┐
│ [Stability]  [Symmetry]  [Loading] │
│    arc          arc         arc    │
│    74            81         62     │
└────────────────────────────────────┘
```

### Session Detail View (eye icon)
- Both graph over time AND summary numbers (user selected Option 3)
- Charts: cadence timeline, impact timeline, gyro/pelvic timeline
- Force Portrait scatter plots (A2)

### Watch Face — BACKLOG
- Wear OS integration
- 3 numbers only: Cadence / Impact / Stability
- Colour coded zones
- **Status: Back burner — build after seeing real outputs**

---

## 10. File Structure (Android Studio Project)

```
NeuroGait/
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/brainsherpa/neurogait/
│   │       ├── App.kt
│   │       ├── ScanActivity.kt
│   │       ├── StreamActivity.kt
│   │       ├── SessionManager.kt
│   │       └── HistoryActivity.kt
│   └── src/main/res/layout/
│       ├── activity_scan.xml
│       ├── activity_stream.xml
│       └── activity_history.xml
├── mdslib/                          ← Movesense AAR module
├── build.gradle
└── settings.gradle
```

---

## 11. Key External References

| Resource | URL |
|---|---|
| Movesense Android Docs | https://www.movesense.com/docs/mobile/android/main/ |
| Movesense API Reference | https://www.movesense.com/docs/esw/api_reference/ |
| MDS AAR Download | https://bitbucket.org/movesense/movesense-mobile-lib/downloads/ |
| AletheiaRun (competitor) | https://aletheia.run |
| AletheiaRun Force Portraits | https://www.aletheia.run/force-portraits |
| AletheiaRun App Store | https://apps.apple.com/us/app/aletheia-run/id6479916698 |
| GitHub reference: ESSI_APP | https://github.com/juliaslocke/ESSI_APP |
| GitHub reference: holmmi | https://github.com/holmmi/Movesense |

---

## 12. Next Session — Action Items

When computer is available:

- [ ] Confirm exact GitHub username (brainsherpa?)
- [ ] Check if `neurogait.xyz` domain is owned → decide package name
- [ ] Create folder: `Documents/BrainSherpa/NeuroGait/`
- [ ] Create GitHub repo: `neurogait` (private, Android .gitignore, with README)
- [ ] Open Android Studio → Get from VCS → clone repo
- [ ] Download MDS AAR from Bitbucket
- [ ] Begin copy-paste build (Claude will provide each file in sequence)

---

## 13. Broader Roadmap (NeuroMetric Ecosystem)

```
NOW          NeuroGait A1 — raw data prototype
             ↓
NEXT         NeuroGait A2 — scored metrics + Force Portrait
             ↓
FUTURE       NeuroGait + NeuroMetric DFa1 — combined session
             (sacrum biomechanics + cardiac HRV in one app)
             ↓
FUTURE       Wear OS watch face — 3-metric live display
             ↓
FUTURE       Coach tab — AI-powered exercise recommendations
                         (Claude API integration)
```

---

*This document was generated from planning conversations with Claude (Anthropic) — May 2026*  
*Next update: after A1 first build session*
