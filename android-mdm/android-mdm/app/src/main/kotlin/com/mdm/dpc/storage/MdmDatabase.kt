package com.mdm.dpc.storage

import android.content.Context
import androidx.room.*

/** §7.3/§19: local cache — executed-command dedup (idempotency backing store for CommandProcessor)
 *  and, as the next increment, cached policy/pending-report queues for offline operation. */
@Entity(tableName = "executed_commands")
data class ExecutedCommandEntity(
    @PrimaryKey val commandId: String,
    val executedAtEpochMillis: Long,
)

@Dao
interface ExecutedCommandDao {
    @Query("SELECT EXISTS(SELECT 1 FROM executed_commands WHERE commandId = :commandId)")
    suspend fun exists(commandId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ExecutedCommandEntity)
}

@Database(entities = [ExecutedCommandEntity::class], version = 1, exportSchema = false)
abstract class MdmDatabase : RoomDatabase() {
    abstract fun executedCommandDao(): ExecutedCommandDao

    companion object {
        @Volatile private var instance: MdmDatabase? = null
        fun get(context: Context): MdmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, MdmDatabase::class.java, "mdm.db")
                    .build().also { instance = it }
            }
    }
}

/** Thin wrapper CommandProcessor depends on — keeps Room specifics out of command-handling logic. */
class ExecutedCommandStore(private val dao: ExecutedCommandDao) {
    suspend fun isExecuted(commandId: String): Boolean = dao.exists(commandId)
    suspend fun markExecuted(commandId: String) =
        dao.insert(ExecutedCommandEntity(commandId, System.currentTimeMillis()))
}
