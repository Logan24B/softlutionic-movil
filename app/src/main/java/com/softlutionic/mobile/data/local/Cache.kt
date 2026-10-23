package com.softlutionic.mobile.data.local
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName = "cache")
data class CacheEntity(@PrimaryKey val key: String, val json: String, val updatedAt: Long)
@Dao interface CacheDao {
 @Query("SELECT * FROM cache WHERE `key` = :key") fun observe(key: String): Flow<CacheEntity?>
 @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun get(key: String): CacheEntity?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: CacheEntity)
 @Query("DELETE FROM cache") suspend fun clear()
}
@Database(entities = [CacheEntity::class], version = 1, exportSchema = true)
abstract class CacheDatabase: RoomDatabase() { abstract fun cache(): CacheDao }
