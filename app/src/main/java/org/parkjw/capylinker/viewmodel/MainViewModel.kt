package org.parkjw.capylinker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.parkjw.capylinker.data.local.LinkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.parkjw.capylinker.data.local.LinkDao

class MainViewModel(private val linkDao: LinkDao) : ViewModel() {

    private val _items = MutableStateFlow<List<LinkItem>>(emptyList())
    val items: StateFlow<List<LinkItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            linkDao.getAllItems().collect {
                _items.value = it
            }
        }
    }

    fun deleteItem(item: LinkItem) {
        viewModelScope.launch {
            linkDao.delete(item)
        }
    }

    fun updateItem(item: LinkItem) {
        viewModelScope.launch {
            linkDao.insert(item)
        }
    }
}