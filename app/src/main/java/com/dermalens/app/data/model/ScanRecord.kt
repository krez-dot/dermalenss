package com.dermalens.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val condition: String,
    val confidence: Float,
    val severity: String,
    val notes: String = "",
    val scanDate: Long = System.currentTimeMillis(),
    val imagePath: String = "",
    val contributedForTraining: Boolean = false,
    // True once ContributionUploadWorker has successfully uploaded this scan's image to Firebase
    // Storage -- separate from contributedForTraining (which just means "user consented and the
    // image was saved locally, pending upload") so the worker knows what's left to do.
    val uploadedForTraining: Boolean = false,
    // Groups scans that are genuinely the same tracked spot over time, so Progress Tracker's
    // trend lines don't conflate unrelated occurrences that just happen to share a condition
    // label (e.g. a wart on one finger vs. an unrelated new wart on a toe). Self-referential: the
    // first scan of a new group gets its own id as this value (set right after insert, once the
    // real id is known); "Scan Again" on a specific trend card copies that same value forward so
    // the new scan explicitly continues it, while "Start New Scan" leaves this null so a fresh
    // group gets created even if the result happens to classify as the same condition.
    val trackGroupId: Int? = null
)