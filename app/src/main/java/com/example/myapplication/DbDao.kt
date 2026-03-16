package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DbDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserItems(items: List<UserEntity>)
    
    @Query("select * from users where uid=:uid")
    suspend fun getUserItemSync(uid: String): UserEntity?

    @Query("select * from users")
    fun getUsers(): LiveData<List<UserEntity?>>
    
    @Query("select * from users")
    suspend fun getUsersSync(): List<UserEntity?>

    @Query("delete from users")
    suspend fun deleteUserItems()

}
