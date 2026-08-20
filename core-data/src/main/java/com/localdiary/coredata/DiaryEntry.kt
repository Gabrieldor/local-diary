package com.localdiary.coredata

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAtEpochMillis: Long,
    val transcript: String,
    val audioFilePath: String?,
    val photoFilePath: String?
)
