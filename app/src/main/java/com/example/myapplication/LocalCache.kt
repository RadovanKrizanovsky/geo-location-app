package com.example.myapplication

import androidx.lifecycle.LiveData

class LocalCache(private val dao: DbDao) {

    suspend fun insertUserItems(items: List<UserEntity>) {
        dao.insertUserItems(items)
    }

    fun getUsers(): LiveData<List<UserEntity?>> = dao.getUsers()
    
    suspend fun getUsersSync(): List<UserEntity?> = dao.getUsersSync()

    suspend fun deleteAllUsers() {
        dao.deleteUserItems()
    }
    
    suspend fun getUserById(userId: String): UserEntity? {
        return dao.getUserItemSync(userId)
    }

}
