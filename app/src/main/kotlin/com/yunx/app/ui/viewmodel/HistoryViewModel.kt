package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.HistoryDao
import com.yunx.app.data.db.HistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val dao: HistoryDao) : ViewModel() {
    val items: StateFlow<List<HistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
    fun clear() = viewModelScope.launch { dao.clear() }

    class Factory(private val dao: HistoryDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(dao) as T
        }
    }
}
