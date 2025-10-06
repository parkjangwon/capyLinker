package org.parkjw.capylinker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.parkjw.capylinker.data.database.LinkEntity
import org.parkjw.capylinker.data.repository.LinkRepository
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val linkRepository: LinkRepository
) : ViewModel() {

    fun updateItem(item: LinkEntity) {
        viewModelScope.launch {
            linkRepository.updateItem(item)
        }
    }
}