package com.packingandmoving.surveyor.viewmodel

import androidx.lifecycle.ViewModel
import com.packingandmoving.surveyor.model.InventoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds an editable in-memory copy of one [InventoryItem]. All edits are local only.
 * TODO: once the backend exposes an update endpoint, persist [item] there when the user saves.
 */
class ItemDetailsViewModel(initialItem: InventoryItem) : ViewModel() {

    private val _item = MutableStateFlow(initialItem)
    val item: StateFlow<InventoryItem> = _item.asStateFlow()

    fun update(transform: (InventoryItem) -> InventoryItem) {
        _item.update(transform)
    }
}
