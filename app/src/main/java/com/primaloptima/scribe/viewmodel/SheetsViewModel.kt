package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class SheetsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScribeApp).database

    val allEntries: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val characters: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeByType("character")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val locations: StateFlow<List<WorldEntry>> =
        db.worldEntryDao().observeByType("location")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createEntry(type: String, name: String, onCreated: (WorldEntry) -> Unit) {
        val template = when (type) {
            "character" -> CHARACTER_FIELDS
            "location"  -> LOCATION_FIELDS
            "faction"   -> FACTION_FIELDS
            "item"      -> ITEM_FIELDS
            "lore"      -> LORE_FIELDS
            "timeline"  -> TIMELINE_FIELDS
            else        -> GENERAL_FIELDS
        }
        val defaultName = when (type) {
            "character" -> "New Character"
            "location"  -> "New Location"
            "faction"   -> "New Faction"
            "item"      -> "New Item"
            "lore"      -> "New Lore Entry"
            "timeline"  -> "New Timeline Event"
            else        -> "New Entry"
        }
        val entry = WorldEntry(
            id         = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
            type       = type,
            name       = name.ifBlank { defaultName },
            fieldsJson = AppJson.encodeToString(template),
            createdAt  = System.currentTimeMillis(),
            updatedAt  = System.currentTimeMillis()
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.worldEntryDao().insert(entry) }
            onCreated(entry)
        }
    }

    fun updateEntry(entry: WorldEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.worldEntryDao().update(entry.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateEntryFields(entry: WorldEntry, fields: List<Field>) {
        val updated = entry.copy(
            fieldsJson = AppJson.encodeToString(fields),
            updatedAt  = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.worldEntryDao().update(updated)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) { db.worldEntryDao().deleteById(id) }
    }

    fun duplicateEntry(id: String) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                db.worldEntryDao().getById(id)
            } ?: return@launch
            val copy = source.copy(
                id        = System.currentTimeMillis().toString() + Math.random().toString().takeLast(7),
                name      = "${source.name} (copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { db.worldEntryDao().insert(copy) }
        }
    }

    enum class SortOption(val label: String, val shortLabel: String) {
        UPDATED_DESC("Recently Updated", "Updated ↓"),
        UPDATED_ASC("Oldest Updated", "Updated ↑"),
        CREATED_DESC("Recently Created", "Created ↓"),
        CREATED_ASC("Oldest Created", "Created ↑"),
        NAME_ASC("Name (A → Z)", "Name A-Z"),
        NAME_DESC("Name (Z → A)", "Name Z-A"),
        TYPE("Category / Type", "Category")
    }

    companion object {

        @Serializable
        data class Field(
            val label: String,
            val value: String = ""
        )

        val CHARACTER_FIELDS = listOf(
            Field("Role", "Protagonist"),
            Field("Aliases / Titles"),
            Field("Species / Race"),
            Field("Arc Status", "Alive"),
            Field("Abilities / Powers"),
            Field("Age"),
            Field("Appearance"),
            Field("Personality & Quirks"),
            Field("Goal / Motivation"),
            Field("Backstory & Origin"),
            Field("Strengths"),
            Field("Weaknesses & Flaws"),
            Field("Relationships & Affiliations"),
            Field("Key Equipment & Items")
        )

        val LOCATION_FIELDS = listOf(
            Field("Region / Realm"),
            Field("Climate & Atmosphere"),
            Field("Inhabitants & Factions"),
            Field("Key Landmarks & POIs"),
            Field("History & Lore"),
            Field("Hazards & Magic Anomalies"),
            Field("Significance to Story")
        )

        val FACTION_FIELDS = listOf(
            Field("Leaders & Key Figures"),
            Field("Core Ideology & Goal"),
            Field("Headquarters & Territory"),
            Field("Allies & Rivals"),
            Field("Military & Resources"),
            Field("Secrets & Weaknesses"),
            Field("Known Influence / Reach")
        )

        val ITEM_FIELDS = listOf(
            Field("Item Classification"),
            Field("Origin & Creator"),
            Field("Powers & Enchantments"),
            Field("Current Possessor / Location"),
            Field("Lore & Legends"),
            Field("Value & Rarity"),
            Field("Curse / Cost / Danger")
        )

        val LORE_FIELDS = listOf(
            Field("Era & Age"),
            Field("Key Figures & Deities"),
            Field("The Legend / True History"),
            Field("Cataclysm & Cause"),
            Field("Consequences"),
            Field("Present Day Cultural Impact"),
            Field("Forbidden Knowledge")
        )

        val TIMELINE_FIELDS = listOf(
            Field("Date / Year / Epoch"),
            Field("Location & Realm"),
            Field("Key Participants & Factions"),
            Field("What Transpired"),
            Field("Casualties & Outcome"),
            Field("Impact on Present Arc"),
            Field("Connected Foreshadowing")
        )

        val GENERAL_FIELDS = listOf(
            Field("Description"),
            Field("Key Details"),
            Field("Notes & References")
        )
    }
}
