package com.topocalcbtp

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "survey_projects")
data class SurveyProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val method: String = "LEVELING"
)

/** A named point of a project (imported, computed or typed). X = Easting, Y = Northing. */
@Entity(
    tableName = "project_points",
    foreignKeys = [ForeignKey(
        entity = SurveyProject::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["projectId", "name"], unique = true)]
)
data class ProjectPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double?,
    val code: String,
    /** Where the point came from: CSV, RAYONNEMENT, CHEMINEMENT, CONVERSION, MANUEL. */
    val source: String
)

/** A saved calculation: its inputs are stored in [payload] (see RecordCodec) so it can be reopened. */
@Entity(
    tableName = "saved_calculations",
    foreignKeys = [ForeignKey(
        entity = SurveyProject::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["projectId"])]
)
data class SavedCalculation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: String,
    val title: String,
    val summary: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
abstract class ProjectDao {
    @Query("SELECT * FROM survey_projects ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<SurveyProject>>

    @Insert
    abstract suspend fun insert(project: SurveyProject): Long

    @Delete
    abstract suspend fun deleteProjectRow(project: SurveyProject)

    @Query("DELETE FROM project_points WHERE projectId = :projectId")
    abstract suspend fun deleteAllPoints(projectId: Long)

    @Query("DELETE FROM saved_calculations WHERE projectId = :projectId")
    abstract suspend fun deleteAllCalculations(projectId: Long)

    /** Deletes the project with its points and calculations (explicitly, even if foreign keys are off). */
    @Transaction
    open suspend fun delete(project: SurveyProject) {
        deleteAllPoints(project.id)
        deleteAllCalculations(project.id)
        deleteProjectRow(project)
    }

    @Query("SELECT * FROM project_points WHERE projectId = :projectId")
    abstract fun observePoints(projectId: Long): Flow<List<ProjectPoint>>

    @Query("SELECT name FROM project_points WHERE projectId = :projectId")
    abstract suspend fun pointNames(projectId: Long): List<String>

    /** A point with an existing name in the same project is replaced (unique index projectId + name). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPoints(points: List<ProjectPoint>)

    @Delete
    abstract suspend fun deletePoint(point: ProjectPoint)

    @Query("SELECT * FROM saved_calculations WHERE projectId = :projectId ORDER BY createdAt DESC")
    abstract fun observeCalculations(projectId: Long): Flow<List<SavedCalculation>>

    @Insert
    abstract suspend fun insertCalculation(calculation: SavedCalculation): Long

    @Delete
    abstract suspend fun deleteCalculation(calculation: SavedCalculation)

    // ---------- backup / restore ----------
    @Query("SELECT * FROM survey_projects ORDER BY createdAt DESC")
    abstract suspend fun allProjects(): List<SurveyProject>

    @Query("SELECT * FROM project_points WHERE projectId = :projectId")
    abstract suspend fun pointsOf(projectId: Long): List<ProjectPoint>

    @Query("SELECT * FROM saved_calculations WHERE projectId = :projectId ORDER BY createdAt DESC")
    abstract suspend fun calculationsOf(projectId: Long): List<SavedCalculation>

    @Query("DELETE FROM project_points")
    abstract suspend fun deleteEveryPoint()

    @Query("DELETE FROM saved_calculations")
    abstract suspend fun deleteEveryCalculation()

    @Query("DELETE FROM survey_projects")
    abstract suspend fun deleteEveryProject()

    @Insert
    abstract suspend fun insertPoints(points: List<ProjectPoint>)

    @Insert
    abstract suspend fun insertCalculations(calculations: List<SavedCalculation>)

    /** Replaces every project by the backup content; returns backup project id -> new database id. */
    @Transaction
    open suspend fun replaceAll(projects: List<BackupProject>): Map<String, Long> {
        deleteEveryPoint()
        deleteEveryCalculation()
        deleteEveryProject()
        val ids = LinkedHashMap<String, Long>()
        projects.forEach { bp ->
            val newId = insert(SurveyProject(name = bp.name, createdAt = bp.createdAt))
            ids[bp.backupId] = newId
            // Unique name per project: keep the first occurrence of a name.
            insertPoints(bp.points.distinctBy { it.name }.map { it.copy(id = 0, projectId = newId) })
            insertCalculations(bp.calculations.map { it.copy(id = 0, projectId = newId) })
        }
        return ids
    }
}

/** v1 (app 0.2/0.3) had only survey_projects. v2 adds points and saved calculations; existing projects are kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `z` REAL, " +
                "`code` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "FOREIGN KEY(`projectId`) REFERENCES `survey_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_project_points_projectId_name` ON `project_points` (`projectId`, `name`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_calculations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`projectId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`projectId`) REFERENCES `survey_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_calculations_projectId` ON `saved_calculations` (`projectId`)"
        )
    }
}

@Database(
    entities = [SurveyProject::class, ProjectPoint::class, SavedCalculation::class],
    version = 2,
    exportSchema = false
)
abstract class TopoDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var INSTANCE: TopoDatabase? = null
        fun get(context: Context): TopoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, TopoDatabase::class.java, "topocalc_projects.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
