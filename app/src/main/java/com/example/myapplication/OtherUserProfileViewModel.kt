package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class OtherUserProfileViewModel(private val repository: DataRepository) : ViewModel() {
    
    private val _userEntity = MutableLiveData<UserEntity?>()
    val userEntity: LiveData<UserEntity?> get() = _userEntity
    
    private val _myGeofence = MutableLiveData<UserEntity?>()
    val myGeofence: LiveData<UserEntity?> get() = _myGeofence
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error
    
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            val cachedUser = repository.getCachedUserById(userId)
            if (cachedUser != null) {
                _userEntity.postValue(cachedUser)
            } else {
                _error.postValue("Používateľ nebol nájdený v okolí")
            }
            
            val myLocation = repository.getCachedUserById("me")
            if (myLocation != null && myLocation.lat != 0.0 && myLocation.lon != 0.0) {
                _myGeofence.postValue(myLocation)
            }
        }
    }
}
