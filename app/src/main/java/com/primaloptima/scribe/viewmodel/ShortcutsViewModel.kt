package com.primaloptima.scribe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.DefaultShortcuts
import com.primaloptima.scribe.util.model.ShortcutAction
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShortcutsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ScribeApp).dataStore

    private val _shortcuts = MutableStateFlow<List<ShortcutAction>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutAction>> = _shortcuts.asStateFlow()

    private val _disabledCategories = MutableStateFlow<Set<String>>(emptySet())
    val disabledCategories: StateFlow<Set<String>> = _disabledCategories.asStateFlow()

    /**
     * Active shortcuts for display on the editor accessory bar:
     * strictly those enabled, belonging to non-disabled categories, in their customized user order.
     */
    val activeBarShortcuts: StateFlow<List<ShortcutAction>> = combine(_shortcuts, _disabledCategories) { list, disabledCats ->
        list.filter { it.isEnabled && it.category !in disabledCats }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val loadedShortcuts = dataStore.getShortcuts()
            val rawDisabledCats = dataStore.getDisabledCategories()

            // Defensively migrate disabled categories:
            // If legacy "symbols" category was disabled by the user, inherit disable state
            // to the newly split categories (brackets, scene_breaks) so migrated items don't suddenly appear uninvited.
            val migratedDisabledCats = rawDisabledCats.toMutableSet()
            if (rawDisabledCats.contains("symbols")) {
                migratedDisabledCats.add(DefaultShortcuts.CAT_BRACKETS)
                migratedDisabledCats.add(DefaultShortcuts.CAT_SCENE_BREAKS)
            }

            _shortcuts.value = loadedShortcuts
            _disabledCategories.value = migratedDisabledCats

            // Persist the migrated states quietly
            save(loadedShortcuts)
            if (migratedDisabledCats != rawDisabledCats) {
                dataStore.setDisabledCategoriesJson(AppJson.encodeToString(migratedDisabledCats))
            }
        }
    }

    fun add(shortcut: ShortcutAction) {
        val list = _shortcuts.value.toMutableList()
        list.add(shortcut)
        save(list)
    }

    fun update(shortcut: ShortcutAction) {
        val list = _shortcuts.value.map {
            if (it.id == shortcut.id) shortcut else it
        }
        save(list)
    }

    fun toggleShortcutEnabled(id: String) {
        val list = _shortcuts.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        save(list)
    }

    fun setShortcutEnabled(id: String, enabled: Boolean) {
        val list = _shortcuts.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        save(list)
    }

    fun delete(id: String) {
        val list = _shortcuts.value.filter { it.id != id }
        save(list)
    }

    fun toggleCategory(categoryId: String) {
        val current = _disabledCategories.value.toMutableSet()
        if (current.contains(categoryId)) {
            current.remove(categoryId)
        } else {
            current.add(categoryId)
        }
        _disabledCategories.value = current
        viewModelScope.launch {
            dataStore.setDisabledCategoriesJson(AppJson.encodeToString(current))
        }
    }

    /**
     * Atomically commits a finalized writing bar order from Edit Mode.
     * Active shortcuts are ordered to strictly match [orderedActiveIds].
     * Any shortcut previously active in the bar that is no longer in [orderedActiveIds]
     * has isEnabled set to false (removed from bar, but preserved intact in the library).
     */
    fun commitActiveBarOrder(orderedActiveIds: List<String>) {
        val currentList = _shortcuts.value
        val orderedIdSet = orderedActiveIds.toSet()
        val currentMap = currentList.associateBy { it.id }

        // 1. Reordered active bar items
        val activeItems = orderedActiveIds.mapNotNull { id ->
            currentMap[id]?.copy(isEnabled = true)
        }

        // 2. Remaining library items
        val remainingItems = currentList.filter { it.id !in orderedIdSet }.map { item ->
            // If item was enabled and not in disabled categories, it was removed from the active bar
            if (item.isEnabled && item.category !in _disabledCategories.value) {
                item.copy(isEnabled = false)
            } else {
                item
            }
        }

        val finalList = activeItems + remainingItems
        save(finalList)
    }

    fun moveActiveShortcut(fromIndex: Int, toIndex: Int) {
        val active = activeBarShortcuts.value
        if (fromIndex !in active.indices || toIndex !in active.indices || fromIndex == toIndex) return
        val targetItem = active[fromIndex]
        val otherItem = active[toIndex]

        val list = _shortcuts.value.toMutableList()
        val origIdxFrom = list.indexOfFirst { it.id == targetItem.id }
        val origIdxTo = list.indexOfFirst { it.id == otherItem.id }
        if (origIdxFrom != -1 && origIdxTo != -1) {
            val item = list.removeAt(origIdxFrom)
            list.add(origIdxTo, item)
            save(list)
        }
    }

    fun reorder(from: Int, to: Int) {
        val list = _shortcuts.value.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        save(list)
    }

    fun resetToDefaults() {
        _disabledCategories.value = emptySet()
        viewModelScope.launch {
            dataStore.setDisabledCategoriesJson(AppJson.encodeToString(emptySet<String>()))
        }
        save(DefaultShortcuts.all)
    }

    private fun save(list: List<ShortcutAction>) {
        _shortcuts.value = list
        viewModelScope.launch {
            dataStore.setShortcutsJson(AppJson.encodeToString(list))
        }
    }

    fun generateId(): String =
        System.currentTimeMillis().toString() + Math.random().toString().takeLast(6)
}
