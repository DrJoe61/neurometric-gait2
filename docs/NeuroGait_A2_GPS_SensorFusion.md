# NeuroGait A2 — GPS + IMU Sensor Fusion
**Feature:** Phone GPS × Movesense Cadence → Real Stride Length  
**Target build:** NeuroGait A2  
**Status:** Designed — pending A1 data validation  
**Date:** May 2026

---

## Core Concept

Combining the phone's GPS with the Movesense IMU at the sacrum creates a **sensor fusion** pipeline that produces research-grade biomechanical data from consumer hardware. Neither sensor alone can deliver what both together can.

---

## The Key Equation

```
GPS speed (m/s)  ÷  (cadence spm ÷ 2 / 60)  =  REAL stride length (m)

Example:
  GPS speed:   3.2 m/s
  Cadence:     172 spm → 86 strides/min → 1.433 strides/sec
  Stride length = 3.2 ÷ 1.433 = 2.23 metres per stride
```

GPS provides ground truth speed. Movesense provides exact cadence. Together they yield **actual stride length** — no body measurement assumptions, no estimation model.

---

## Sensor Capability Comparison

| Metric | Phone GPS alone | Movesense IMU alone | Fused together |
|---|---|---|---|
| Speed | ✅ Real (±2-5%) | ❌ Estimated | ✅ Real |
| Distance | ✅ Real | ❌ Estimated | ✅ Real |
| Pace (min/km) | ✅ Real | ❌ Estimated | ✅ Real |
| Cadence (spm) | ❌ None | ✅ Exact | ✅ Exact |
| Stride length | ❌ None | ❌ Estimated | ✅ **Real** |
| Impact force | ❌ None | ✅ Exact | ✅ Exact |
| Pelvic movement | ❌ None | ✅ Exact | ✅ Exact |
| Works indoors | ✅ | ✅ | ⚠️ GPS drops |
| Works in tunnels | ❌ | ✅ | ⚠️ GPS drops |
| Treadmill | ❌ | ✅ | ⚠️ Manual speed input |

---

## What Real Stride Length Unlocks (A2 Metrics)

Once stride length is real rather than estimated:

| Metric | Calculation | Significance |
|---|---|---|
| **Stride length variability** | Std deviation per 30s window | Injury risk marker |
| **Cadence vs stride tradeoff** | Is speed gain from cadence↑ or stride↑? | Running economy |
| **Ground contact estimate** | Duration of impact signal per step | Efficiency indicator |
| **Running economy trend** | Stride length drift over session | Fatigue detection |
| **Fatigue index** | Stride shortening rate in final km | Training load marker |
| **Left/right asymmetry** | Stride variation pattern (single sensor approx) | Injury screening |

---

## GPS Accuracy & Honest Caveats

### Samsung Galaxy A26 GPS
- Standard single-frequency GPS (not dual-frequency L5)
- Accuracy: typically 3-8m in open sky, worse in urban/tree cover
- Lock time: 30-60 seconds from cold start — cadence ready before GPS is

### Reliability Rules for the App
```
GPS accuracy < 5m   → stride length CONFIRMED  ✅ (show in green)
GPS accuracy 5-10m  → stride length ESTIMATED  ⚠️ (show in amber)
GPS accuracy > 10m  → stride length UNRELIABLE ❌ (hide or grey out)
```

### Treadmill Mode
- GPS shows zero movement → stride length calculation breaks
- Solution: user inputs treadmill speed manually (km/h)
- App uses manual speed in place of GPS speed
- All other metrics (cadence, impact, gyro) work perfectly on treadmill
- Flag session as TREADMILL in CSV and history

---

## A1 GPS Addition (Minimal)

Even in the A1 prototype, GPS columns are added to the CSV for free — one permission, a few lines of code. No display change needed yet.

**Additional permission in AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```

**Additional CSV columns in A1:**
```
gps_lat, gps_lon, gps_speed_ms, gps_accuracy_m
```

Stride length column added but marked as derived:
```
stride_length_m  (blank if GPS accuracy > 10m)
```

---

## Full A2 CSV Format

```
timestamp_ms,
accX, accY, accZ,
gyrX, gyrY, gyrZ,
step_count,
cadence_spm,
cadence_10s_rolling,
peak_impact_g,
impact_newtons,
loading_rate,
gps_lat,
gps_lon,
gps_speed_ms,
gps_accuracy_m,
stride_length_m,
stride_length_reliable,
pelvic_rotation_deg,
pelvic_sway_deg,
session_mode
```

`session_mode` = OUTDOOR / TREADMILL / INDOOR

---

## A2 Live Screen Additions

### Updated Top Half (Big Numbers)
```
┌──────────────────────────────────────────┐
│  CADENCE      STRIDE        PACE         │
│   172         2.23m        5:12          │
│   spm         real ✅      min/km        │
│                                          │
│  Impact: 2.8g    Distance: 3.42km        │
└──────────────────────────────────────────┘
```

### GPS Status Indicator
Small coloured dot in corner:
- 🟢 GPS locked, accurate
- 🟡 GPS marginal
- 🔴 GPS lost / treadmill mode

---

## A2 Session Detail — New Graphs

Added to the eye-icon session view:

1. **Stride length timeline** — does it hold steady or drop with fatigue?
2. **Cadence × stride length scatter** — speed strategy fingerprint
3. **GPS route map** — coloured by impact force (heat map of hard-landing zones)
4. **Fatigue curve** — cadence + stride + impact overlaid, last 20% of session

---

## Roadmap Position

```
A1  →  Raw IMU data + basic cadence + GPS columns in CSV
        ↓
A2  →  Real stride length + scored metrics + GPS route map
        ↓
A3  →  Force Portrait + full biomechanics dashboard
        ↓
A4  →  DFa1/HRV integration + AI coach tab
        ↓
Watch  →  Wear OS 3-metric face (cadence / impact / stride)
```

---

## Key Research References

- **Stride length from IMU + GPS fusion:** Felici & Moraiti (2021) — sacrum accelerometer valid for step detection in field conditions
- **Cadence optimal zone:** 170-180 spm reduces impact loading (Heiderscheit et al. 2011)
- **Stride variability as injury predictor:** Meardon et al. (2011) — higher variability correlates with stress fracture risk
- **Treadmill vs overground:** Riley et al. (2008) — cadence comparable, stride length slightly shorter on treadmill

---

*Designed during planning session — May 2026*  
*To be implemented after A1 data validation*  
*Part of the NeuroGait / BrainSherpa / NeuroMetric ecosystem*
