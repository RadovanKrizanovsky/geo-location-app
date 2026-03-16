package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AuthViewModel(private val dataRepository: DataRepository) : ViewModel() {
    
    val username = MutableLiveData<String>()
    val email = MutableLiveData<String>()
    val password = MutableLiveData<String>()
    
    private val _registrationResult = MutableLiveData<Pair<String, User?>>()
    val registrationResult: LiveData<Pair<String, User?>> get() = _registrationResult
    
    private val _loginResult = MutableLiveData<Pair<String, User?>>()
    val loginResult: LiveData<Pair<String, User?>> get() = _loginResult

    fun registerUser() {
        viewModelScope.launch {
            _registrationResult.postValue(
                dataRepository.apiRegisterUser(
                    username.value ?: "",
                    email.value ?: "",
                    password.value ?: ""
                )
            )
        }
    }
    
    fun loginUser() {
        viewModelScope.launch {
            _loginResult.postValue(
                dataRepository.apiLoginUser(
                    email.value ?: "",
                    password.value ?: ""
                )
            )
        }
    }
    
    fun clearForm() {
        username.value = ""
        email.value = ""
        password.value = ""
        _loginResult.value = null
        _registrationResult.value = null
    }
}
