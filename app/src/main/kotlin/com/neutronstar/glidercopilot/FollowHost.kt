package com.neutronstar.glidercopilot

import android.content.Context
import com.neutronstar.glidy.social.FollowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** S17 — pilotes suivis, gardés sur ce téléphone (préférences privées « glidy_follows »). S18 : table `follows`. */
class FollowHost(context: Context) : FollowStore {
    private val prefs = context.applicationContext.getSharedPreferences("glidy_follows", Context.MODE_PRIVATE)

    override suspend fun load(): Set<String> = withContext(Dispatchers.IO) { prefs.getStringSet(KEY, emptySet()).orEmpty().toSet() }

    override suspend fun save(ids: Set<String>) = withContext(Dispatchers.IO) { prefs.edit().putStringSet(KEY, HashSet(ids)).apply() }

    private companion object { const val KEY = "following" }
}
