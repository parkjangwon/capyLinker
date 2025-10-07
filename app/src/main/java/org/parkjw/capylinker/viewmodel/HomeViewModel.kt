
package org.parkjw.capylinker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.parkjw.capylinker.data.database.LinkEntity
import org.parkjw.capylinker.data.repository.LinkRepository
import org.parkjw.capylinker.ui.screens.Link
import javax.inject.Inject
import kotlin.collections.filter
import kotlin.collections.flatMap

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val linkRepository: LinkRepository,
    private val settingsRepository: org.parkjw.capylinker.data.repository.SettingsRepository
) : ViewModel() {

    val language = settingsRepository.language
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val allLinks: StateFlow<List<Link>> = linkRepository.getAllLinks()
        .map { entities ->
            entities.map { entity ->
                Link(
                    url = entity.url,
                    title = entity.title,
                    summary = entity.summary,
                    tags = entity.tags,
                    isAnalyzing = entity.isAnalyzing,
                    thumbnailUrl = entity.thumbnailUrl
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val links: StateFlow<List<Link>> = combine(allLinks, _selectedTag) { links, tag ->
        if (tag == null) {
            links
        } else {
            links.filter { it.tags.contains(tag) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTags: StateFlow<List<String>> = allLinks.map { links ->
        links.flatMap { it.tags }.distinct().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun showAddLinkDialog() {
        _showAddDialog.value = true
    }

    fun hideAddLinkDialog() {
        _showAddDialog.value = false
    }

    fun saveLink(url: String) {
        hideAddLinkDialog()
        viewModelScope.launch {
            linkRepository.saveLink(url)
        }
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun deleteLink(link: Link) {
        viewModelScope.launch {
            val entity = LinkEntity(
                url = link.url,
                title = link.title,
                summary = link.summary,
                tags = link.tags,
                thumbnailUrl = link.thumbnailUrl
            )
            linkRepository.deleteLink(entity)
        }
    }
}
