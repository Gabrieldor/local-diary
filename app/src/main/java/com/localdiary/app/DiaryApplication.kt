package com.localdiary.app

import android.app.Application
import com.localdiary.app.ml.WhisperEngineHolder
import com.localdiary.coredata.DiaryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class DiaryApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database: DiaryDatabase by lazy { DiaryDatabase.getInstance(this) }
    val whisperEngineHolder: WhisperEngineHolder by lazy { WhisperEngineHolder(this) }

    override fun onCreate() {
        super.onCreate()
        whisperEngineHolder.loadInBackground(applicationScope)
    }
}
