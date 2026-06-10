package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PythonFileDao {
    @Query("SELECT * FROM python_files ORDER BY lastUpdated DESC")
    fun getAllFiles(): Flow<List<PythonFile>>

    @Query("SELECT * FROM python_files WHERE name = :name LIMIT 1")
    suspend fun getFileByName(name: String): PythonFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: PythonFile)

    @Delete
    suspend fun deleteFile(file: PythonFile)

    @Query("DELETE FROM python_files WHERE name = :name")
    suspend fun deleteFileByName(name: String)
}
