package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PythonFile::class], version = 1, exportSchema = false)
abstract class PythonDatabase : RoomDatabase() {
    abstract fun pythonFileDao(): PythonFileDao

    companion object {
        @Volatile
        private var INSTANCE: PythonDatabase? = null

        fun getDatabase(context: Context): PythonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PythonDatabase::class.java,
                    "python_studio_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
