package com.example.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()

    suspend fun insert(message: Message) {
        messageDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        messageDao.clearHistory()
    }
}
