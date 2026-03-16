package com.example.myapplication

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.switchMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: DataRepository) : ViewModel() {

    private val _refreshTrigger = MutableLiveData(0)
    
    val feed_items: LiveData<List<UserEntity?>> = _refreshTrigger.switchMap {
        repository.getUsers()
    }

    val loading = MutableLiveData(false)

    private val _message = MutableLiveData<Evento<String>>()
    val message: LiveData<Evento<String>>
        get() = _message

    init {
        refreshData()
    }
    
    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val errorMessage = repository.apiGeofenceUsers()
            if (errorMessage.isNotEmpty()) {
                Log.e("FeedViewModel", "Error loading geofence: $errorMessage")
                _message.postValue(Evento(errorMessage))
            }
            loading.postValue(false)
            _refreshTrigger.postValue((_refreshTrigger.value ?: 0) + 1)
        }
    }
}
