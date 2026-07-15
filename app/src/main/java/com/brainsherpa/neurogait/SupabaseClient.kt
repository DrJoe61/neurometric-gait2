package com.brainsherpa.neurogait

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient as SbClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient as createSbClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.runBlocking

private const val SUPABASE_URL = "https://honrpfojbjfnjdcrpqsh.supabase.co"
private const val SUPABASE_ANON_KEY = "sb_publishable_3Qe4YHXFlwrpkl_WGAfN8g_LXhCqLZ4"

/**
 * Singleton SupabaseClient for the NeuroMetric Suite (shared project).
 * Postgrest for the suite_metrics writes, Auth for Google Sign-In with the
 * session persisted to SharedPreferences (survives process death).
 */
object SupabaseClient {
    private lateinit var appContext: Context

    suspend fun init(context: Context) {
        appContext = context.applicationContext
        client.auth.awaitInitialization()
    }

    @JvmStatic
    fun initBlocking(context: Context) = runBlocking { init(context) }

    val client: SbClient by lazy {
        require(::appContext.isInitialized) {
            "SupabaseClient.init(context) must be called before client access"
        }
        createSbClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SettingsSessionManager(
                    settings = SharedPreferencesSettings(
                        appContext.getSharedPreferences(
                            "supabase_session",
                            Context.MODE_PRIVATE
                        )
                    )
                )
                autoLoadFromStorage = true
            }
        }
    }
}
