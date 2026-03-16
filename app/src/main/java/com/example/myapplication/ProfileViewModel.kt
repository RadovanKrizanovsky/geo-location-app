package com.example.myapplication

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel(private val dataRepository: DataRepository) : ViewModel() {
    val sharingLocation = MutableLiveData<Boolean?>(null)
}
