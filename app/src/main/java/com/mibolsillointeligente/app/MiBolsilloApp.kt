package com.mibolsillointeligente.app

import android.app.Application
import com.mibolsillointeligente.app.data.local.AppDatabase
import com.mibolsillointeligente.app.data.repository.FinanceRepository
import com.mibolsillointeligente.app.notifications.ReminderNotificationHelper
import com.mibolsillointeligente.app.settings.AppPreferencesDataStore

class MiBolsilloApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var preferences: AppPreferencesDataStore
        private set

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = AppPreferencesDataStore(this)
        repository = FinanceRepository(this, database, preferences)
        ReminderNotificationHelper.createChannel(this)
    }
}
