package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "python_files")
data class PythonFile(
    @PrimaryKey val name: String, // E.g. "main.py"
    val content: String,
    val isFavorite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) : Serializable
