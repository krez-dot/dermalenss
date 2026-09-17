package com.dermalens.app.data.db

import androidx.room.*
import com.dermalens.app.data.model.ScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanRecord): Long

    @Query("SELECT * FROM scan_records WHERE userId = :userId ORDER BY scanDate DESC")
    fun getScansByUser(userId: Int): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE userId = :userId ORDER BY scanDate DESC")
    suspend fun getScansByUserOnce(userId: Int): List<ScanRecord>

    @Query("SELECT COUNT(*) FROM scan_records WHERE userId = :userId")
    suspend fun getScanCount(userId: Int): Int

    @Query("SELECT * FROM scan_records WHERE userId = :userId AND condition = :condition ORDER BY scanDate ASC")
    suspend fun getScansByCondition(userId: Int, condition: String): List<ScanRecord>

    @Query("DELETE FROM scan_records WHERE id = :scanId")
    suspend fun deleteScan(scanId: Int)

    @Query("SELECT * FROM scan_records WHERE id = :scanId LIMIT 1")
    suspend fun getScanById(scanId: Int): ScanRecord?

    @Query("SELECT DISTINCT condition FROM scan_records WHERE userId = :userId")
    suspend fun getConditionsByUser(userId: Int): List<String>

    // Scans the user consented to contribute, saved locally, but not yet uploaded to Firebase
    // Storage -- what ContributionUploadWorker works through on each run.
    @Query("SELECT * FROM scan_records WHERE contributedForTraining = 1 AND uploadedForTraining = 0 AND imagePath != ''")
    suspend fun getPendingContributions(): List<ScanRecord>

    @Query("UPDATE scan_records SET uploadedForTraining = 1 WHERE id = :scanId")
    suspend fun markContributionUploaded(scanId: Int)

    @Query("UPDATE scan_records SET notes = :notes WHERE id = :scanId")
    suspend fun updateNotes(scanId: Int, notes: String)
}