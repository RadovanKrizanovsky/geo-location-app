package com.example.myapplication

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapViewModel(private val repository: DataRepository) : ViewModel() {
    
    private val TAG = "MapViewModel"
    
    private val _refreshTrigger = MutableLiveData(0)
    
    val users: LiveData<List<UserEntity?>> = _refreshTrigger.switchMap {
        repository.getUsers()
    }
    
    val loading = MutableLiveData(false)
    
    init {
        refreshUsers()
    }
    
    fun refreshUsers() {
        Log.d(TAG, "Manual refresh triggered")
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val errorMessage = repository.apiGeofenceUsers()
            if (errorMessage.isNotEmpty()) {
                Log.e(TAG, "Error refreshing users: $errorMessage")
            } else {
                Log.d(TAG, "Users refreshed successfully")
            }
            loading.postValue(false)
            _refreshTrigger.postValue((_refreshTrigger.value ?: 0) + 1)
        }
    }
}
