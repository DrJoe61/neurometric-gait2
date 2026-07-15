package com.brainsherpa.neurogait

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

@Serializable
data class SuiteMetric(
    val app_source: String,
    val metric_type: String,
    val value: Double,
    val recorded_at: String
)

@Serializable
data class PaceRawSession(
    val app_source: String,
    val recorded_at: String,
    val sample_count: Int,
    val samples_csv: String
)

/** One completed NeuroGait walk/jog, summarized. */
data class PaceSessionMetrics(
    val recordedAt: String,      // ISO-8601 UTC
    val sessionDurationS: Int,
    val stepsTotal: Int,
    val cadenceSpmAvg: Double,
    val cadenceSpmPeak: Double,
    val peakImpactG: Double,
    val meanImpactG: Double
)

object SuiteSyncRepository {

    private const val APP_SOURCE = "neurometric_pace"
    private val supabase get() = SupabaseClient.client

    /** Pushes one session as ~6 rows tagged neurometric_pace. Silently skips if not signed in. */
    suspend fun pushSessionMetrics(m: PaceSessionMetrics) {
        try {
            supabase.auth.currentUserOrNull() ?: run {
                Log.w("SuiteSync", "Not signed in - skipping Supabase push")
                return
            }

            val rows = listOf(
                SuiteMetric(APP_SOURCE, "session_duration_s", m.sessionDurationS.toDouble(), m.recordedAt),
                SuiteMetric(APP_SOURCE, "steps_total", m.stepsTotal.toDouble(), m.recordedAt),
                SuiteMetric(APP_SOURCE, "cadence_spm_avg", m.cadenceSpmAvg, m.recordedAt),
                SuiteMetric(APP_SOURCE, "cadence_spm_peak", m.cadenceSpmPeak, m.recordedAt),
                SuiteMetric(APP_SOURCE, "peak_impact_g", m.peakImpactG, m.recordedAt),
                SuiteMetric(APP_SOURCE, "mean_impact_g", m.meanImpactG, m.recordedAt)
            )

            supabase.postgrest["suite_metrics"].upsert(rows) {
                onConflict = "user_id,metric_type,recorded_at"
                ignoreDuplicates = true
            }
            Log.d("SuiteSync", "Pushed ${rows.size} pace metrics to Supabase")
        } catch (e: Exception) {
            Log.w("SuiteSync", "Push failed: ${e.message}")
        }
    }

    /**
     * Pushes the full raw IMU stream for one session as a single row (CSV in a text
     * column) so the L/R, ground-contact and pelvic graphs can be rebuilt later.
     * Replaces the on-phone CSV file. Silently skips if not signed in.
     */
    suspend fun pushRawSession(recordedAt: String, sampleCount: Int, samplesCsv: String) {
        try {
            supabase.auth.currentUserOrNull() ?: run {
                Log.w("SuiteSync", "Not signed in - skipping raw upload")
                return
            }
            supabase.postgrest["pace_session_raw"].insert(
                PaceRawSession(APP_SOURCE, recordedAt, sampleCount, samplesCsv)
            )
            Log.d("SuiteSync", "Pushed raw session ($sampleCount samples) to Supabase")
        } catch (e: Exception) {
            Log.w("SuiteSync", "Raw push failed: ${e.message}")
        }
    }
}
