package com.brainsherpa.neurogait

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SupabaseIdentity {

    private const val PREFS = "pace_identity"

    private fun getName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("athlete_name", null)
    }

    fun isSignedInWithGoogle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefsSaysSignedIn = !prefs.getString("supabase_real_user_id", null).isNullOrBlank()
        if (!prefsSaysSignedIn) return false
        // Defense in depth: confirm Supabase actually has a live session.
        val supabaseHasSession = SupabaseClient.client.auth.currentUserOrNull() != null
        return supabaseHasSession
    }

    suspend fun signInWithGoogle(context: Context, googleIdToken: String, rawNonce: String): Result<Unit> {
        return try {
            val supabase = SupabaseClient.client
            val anonId = supabase.auth.currentUserOrNull()?.id

            supabase.auth.signInWith(IDToken) {
                provider = Google
                idToken = googleIdToken
                nonce = rawNonce
            }

            val realId = supabase.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("No user after Google sign-in"))

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString("supabase_real_user_id", realId).apply()

            if (anonId != null && anonId != realId) {
                try {
                    supabase.postgrest.rpc(
                        "migrate_anon_metrics",
                        buildJsonObject {
                            put("anon_uuid", anonId)
                            put("real_uuid", realId)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("SupabaseIdentity", "Migration RPC failed: ${e.message}")
                }
            }

            supabase.postgrest["profiles"].upsert(
                value = buildJsonObject {
                    put("user_id", realId)
                    put("athlete_name", getName(context) ?: "Athlete")
                    put("email", supabase.auth.currentUserOrNull()?.email ?: "")
                    put("updated_at", Clock.System.now().toString())
                }
            ) {
                onConflict = "user_id"
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseIdentity", "signInWithGoogle failed: ${e.message}")
            Result.failure(e)
        }
    }
}
