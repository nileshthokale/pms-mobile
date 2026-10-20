package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.AuditLogEntry
import com.example.model.MedicineCatalogItem
import com.example.model.PrescriptionRequest
import com.example.model.StaffAccount
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface PharmacyDao {

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeUserProfile(userId: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfile(userId: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfile)

    @Query("SELECT * FROM prescriptions WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun observePrescriptions(userId: String): Flow<List<PrescriptionRequest>>

    @Query("SELECT * FROM prescriptions WHERE id = :prescriptionId LIMIT 1")
    suspend fun getPrescriptionById(prescriptionId: String): PrescriptionRequest?

    @Query("SELECT * FROM prescriptions WHERE userId = :userId")
    suspend fun getAllPrescriptionsForUser(userId: String): List<PrescriptionRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrescription(prescription: PrescriptionRequest)

    @Update
    suspend fun updatePrescription(prescription: PrescriptionRequest)

    @Query("DELETE FROM prescriptions WHERE id = :prescriptionId")
    suspend fun deletePrescriptionById(prescriptionId: String)

    @Query("SELECT * FROM catalog_items WHERE userId = :userId ORDER BY LOWER(itemName) ASC")
    fun observeCatalogItems(userId: String): Flow<List<MedicineCatalogItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogItem(item: MedicineCatalogItem)

    @Query("DELETE FROM catalog_items WHERE id = :itemId")
    suspend fun deleteCatalogItemById(itemId: String)

    @Query("SELECT * FROM staff_accounts WHERE userId = :userId ORDER BY LOWER(staffName) ASC")
    fun observeStaffAccounts(userId: String): Flow<List<StaffAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaffAccount(staff: StaffAccount)

    @Query("DELETE FROM staff_accounts WHERE id = :staffId")
    suspend fun deleteStaffAccountById(staffId: String)

    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: AuditLogEntry)
}

@Database(
    entities = [
        UserProfile::class,
        PrescriptionRequest::class,
        MedicineCatalogItem::class,
        StaffAccount::class,
        AuditLogEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PharmacyDatabase : RoomDatabase() {
    abstract fun pharmacyDao(): PharmacyDao

    companion object {
        @Volatile
        private var INSTANCE: PharmacyDatabase? = null

        fun getInstance(context: Context): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "pharmacy_prescription_bridge.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
