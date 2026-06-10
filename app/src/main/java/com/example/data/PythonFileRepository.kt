package com.example.data

import kotlinx.coroutines.flow.Flow

class PythonFileRepository(private val dao: PythonFileDao) {
    val allFiles: Flow<List<PythonFile>> = dao.getAllFiles()

    suspend fun getFileByName(name: String): PythonFile? = dao.getFileByName(name)

    suspend fun insertFile(file: PythonFile) = dao.insertFile(file)

    suspend fun deleteFile(file: PythonFile) = dao.deleteFile(file)

    suspend fun deleteFileByName(name: String) = dao.deleteFileByName(name)
}
