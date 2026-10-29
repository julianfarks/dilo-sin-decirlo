package app.dilosindecirlo.data

import android.content.Context
import app.dilosindecirlo.stats.StatsCodec
import app.dilosindecirlo.stats.StatsData
import java.io.File

/** Catálogo integrado (assets/cards.json), cartas personalizadas y favoritas. Todo local, sin Internet. */
class CardRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
    private val customFile = File(context.filesDir, "custom_cards.json")

    fun loadCatalog(): Catalog {
        val json = context.assets.open("cards.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return CardCodec.parseCatalog(json)
    }

    fun loadCustom(): List<Card> {
        if (!customFile.exists()) return emptyList()
        return try {
            CardCodec.parseCustom(customFile.readText(Charsets.UTF_8)) { error("las cartas guardadas deben traer id") }
        } catch (e: Exception) {
            // Archivo dañado: se conserva una copia para no perder los datos y se arranca vacío.
            runCatching { customFile.copyTo(File(context.filesDir, "custom_cards.corrupt.json"), overwrite = true) }
            emptyList()
        }
    }

    /** Escritura atómica: primero a un temporal y luego se renombra, para no corromper el archivo. */
    fun saveCustom(cards: List<Card>) {
        val tmp = File(context.filesDir, "custom_cards.json.tmp")
        tmp.writeText(CardCodec.customToJson(cards), Charsets.UTF_8)
        if (!tmp.renameTo(customFile)) {
            customFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun loadFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun saveFavorites(ids: Set<String>) {
        prefs.edit().putStringSet("favorites", ids).apply()
    }
}

class StatsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun load(): StatsData {
        val json = prefs.getString("stats_json", null) ?: return StatsData()
        return try {
            StatsCodec.fromJson(json)
        } catch (e: Exception) {
            StatsData()
        }
    }

    fun save(stats: StatsData) {
        prefs.edit().putString("stats_json", StatsCodec.toJson(stats)).apply()
    }

    fun reset() {
        prefs.edit().remove("stats_json").apply()
    }
}

data class AppSettings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val animations: Boolean = true,
    val voiceAssist: Boolean = false,
    val voiceAutoApply: Boolean = false,
    val keepScreenOn: Boolean = true,
    val hiddenCategories: Set<String> = emptySet(),
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        sound = prefs.getBoolean("sound", true),
        vibration = prefs.getBoolean("vibration", true),
        animations = prefs.getBoolean("animations", true),
        voiceAssist = prefs.getBoolean("voiceAssist", false),
        voiceAutoApply = prefs.getBoolean("voiceAutoApply", false),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        hiddenCategories = prefs.getStringSet("hiddenCategories", emptySet())?.toSet() ?: emptySet(),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("sound", s.sound)
            .putBoolean("vibration", s.vibration)
            .putBoolean("animations", s.animations)
            .putBoolean("voiceAssist", s.voiceAssist)
            .putBoolean("voiceAutoApply", s.voiceAutoApply)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putStringSet("hiddenCategories", s.hiddenCategories)
            .apply()
    }

    fun loadTeamNames(): List<String> {
        val raw = prefs.getString("teamNames", null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun saveTeamNames(names: List<String>) {
        prefs.edit().putString("teamNames", names.joinToString("\n")).apply()
    }
}
