package ru.distillarium.batches

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Пользовательская база. Контент-пакет её не знает и не трогает:
 * обновление и откат контента не могут изменить историю партий.
 */
class UserDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                volume REAL NOT NULL,
                started_at INTEGER NOT NULL,
                stage TEXT NOT NULL DEFAULT '',
                notes TEXT,
                recipe_id TEXT,
                status TEXT NOT NULL DEFAULT 'active',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE batch_steps (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id INTEGER NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                due_at INTEGER NOT NULL,
                done_at INTEGER,
                wiki_id TEXT,
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id INTEGER NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
                at INTEGER NOT NULL,
                gravity REAL NOT NULL,
                temp REAL,
                abv_calc REAL NOT NULL,
                note TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE journal (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id INTEGER NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
                at INTEGER NOT NULL,
                kind TEXT NOT NULL,
                text TEXT NOT NULL,
                photo_uri TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE calcs_saved (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id INTEGER,
                calc_id TEXT NOT NULL,
                inputs_json TEXT NOT NULL,
                result_json TEXT,
                at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE favorites (
                wiki_id TEXT PRIMARY KEY,
                saved_at INTEGER NOT NULL,
                title_at_save TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_steps_batch ON batch_steps(batch_id, position)")
        db.execSQL("CREATE INDEX idx_steps_due ON batch_steps(done_at, due_at)")
        db.execSQL("CREATE INDEX idx_measurements_batch ON measurements(batch_id, at)")
        db.execSQL("CREATE INDEX idx_journal_batch ON journal(batch_id, at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Первая версия схемы. Здесь появятся миграции с сохранением данных.
    }

    fun insertBatch(batch: Batch, steps: List<BatchStep>): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val batchId = db.insertOrThrow("batches", null, batch.toValues())
            steps.forEachIndexed { index, step ->
                db.insertOrThrow(
                    "batch_steps",
                    null,
                    step.toValues(batchId).apply { put("position", index) },
                )
            }
            db.setTransactionSuccessful()
            batchId
        } finally {
            db.endTransaction()
        }
    }

    fun updateBatchStatus(batchId: Long, status: BatchStatus, now: Long) {
        writableDatabase.update(
            "batches",
            ContentValues().apply {
                put("status", status.storage)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(batchId.toString()),
        )
    }

    fun updateBatchNotes(batchId: Long, notes: String?, now: Long) {
        writableDatabase.update(
            "batches",
            ContentValues().apply {
                put("notes", notes)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(batchId.toString()),
        )
    }

    fun setStepDone(stepId: Long, batchId: Long, doneAt: Long?, now: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update(
                "batch_steps",
                ContentValues().apply { put("done_at", doneAt) },
                "id = ?",
                arrayOf(stepId.toString()),
            )
            refreshStage(db, batchId, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }


    fun addStep(step: BatchStep, now: Long): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val position = nextPosition(db, step.batchId)
            val id = db.insertOrThrow(
                "batch_steps",
                null,
                step.toValues(step.batchId).apply { put("position", position) },
            )
            refreshStage(db, step.batchId, now)
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
        }
    }

    fun addMeasurement(measurement: Measurement): Long = writableDatabase.insertOrThrow(
        "measurements",
        null,
        ContentValues().apply {
            put("batch_id", measurement.batchId)
            put("at", measurement.at)
            put("gravity", measurement.gravity)
            put("temp", measurement.temp)
            put("abv_calc", measurement.abvCalc)
            put("note", measurement.note)
        },
    )

    fun addJournal(entry: JournalEntry): Long = writableDatabase.insertOrThrow(
        "journal",
        null,
        ContentValues().apply {
            put("batch_id", entry.batchId)
            put("at", entry.at)
            put("kind", entry.kind.storage)
            put("text", entry.text)
            put("photo_uri", entry.photoUri)
        },
    )

    fun batches(): List<Batch> = readableDatabase.query(
        "batches",
        null,
        null,
        null,
        null,
        null,
        "CASE status WHEN 'active' THEN 0 WHEN 'completed' THEN 1 ELSE 2 END, started_at DESC",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toBatch()) }
    }

    fun batch(batchId: Long): Batch? = readableDatabase.query(
        "batches",
        null,
        "id = ?",
        arrayOf(batchId.toString()),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toBatch() else null }

    fun steps(batchId: Long): List<BatchStep> = readableDatabase.query(
        "batch_steps",
        null,
        "batch_id = ?",
        arrayOf(batchId.toString()),
        null,
        null,
        "position ASC, due_at ASC",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toStep()) }
    }

    fun measurements(batchId: Long): List<Measurement> = readableDatabase.query(
        "measurements",
        null,
        "batch_id = ?",
        arrayOf(batchId.toString()),
        null,
        null,
        "at ASC",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toMeasurement()) }
    }

    fun journal(batchId: Long): List<JournalEntry> = readableDatabase.query(
        "journal",
        null,
        "batch_id = ?",
        arrayOf(batchId.toString()),
        null,
        null,
        "at DESC",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.toJournal()) }
    }

    fun dueReminders(now: Long): List<DueReminder> {
        val sql = """
            SELECT s.id, s.batch_id, s.title, s.due_at, s.done_at, s.wiki_id, s.position,
                   b.title AS batch_title
            FROM batch_steps s
            JOIN batches b ON b.id = s.batch_id
            WHERE s.done_at IS NULL
              AND b.status = 'active'
              AND s.due_at <= ?
            ORDER BY s.due_at ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(now.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DueReminder(
                            batchTitle = cursor.getString(
                                cursor.getColumnIndexOrThrow("batch_title"),
                            ),
                            step = cursor.toStep(),
                        ),
                    )
                }
            }
        }
    }

    private fun refreshStage(db: SQLiteDatabase, batchId: Long, now: Long) {
        db.execSQL(
            """
            UPDATE batches
            SET stage = COALESCE((
                    SELECT title FROM batch_steps
                    WHERE batch_id = ? AND done_at IS NULL
                    ORDER BY due_at ASC, position ASC
                    LIMIT 1
                ), ''),
                updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            arrayOf(batchId, now, batchId),
        )
    }

    private fun nextPosition(db: SQLiteDatabase, batchId: Long): Int =
        db.rawQuery(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM batch_steps WHERE batch_id = ?",
            arrayOf(batchId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun Batch.toValues(): ContentValues = ContentValues().apply {
        put("title", title)
        put("type", kind.storage)
        put("volume", volume)
        put("started_at", startedAt)
        put("stage", stage)
        put("notes", notes)
        put("recipe_id", recipeId)
        put("status", status.storage)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun BatchStep.toValues(batchId: Long): ContentValues = ContentValues().apply {
        put("batch_id", batchId)
        put("title", title)
        put("due_at", dueAt)
        put("done_at", doneAt)
        put("wiki_id", wikiId)
        put("position", position)
    }

    private fun Cursor.toBatch(): Batch = Batch(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        kind = BatchKind.fromStorage(getString(getColumnIndexOrThrow("type"))),
        volume = getDouble(getColumnIndexOrThrow("volume")),
        startedAt = getLong(getColumnIndexOrThrow("started_at")),
        stage = getString(getColumnIndexOrThrow("stage")).orEmpty(),
        notes = getStringOrNull("notes"),
        recipeId = getStringOrNull("recipe_id"),
        status = BatchStatus.fromStorage(getString(getColumnIndexOrThrow("status"))),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
    )

    private fun Cursor.toStep(): BatchStep = BatchStep(
        id = getLong(getColumnIndexOrThrow("id")),
        batchId = getLong(getColumnIndexOrThrow("batch_id")),
        title = getString(getColumnIndexOrThrow("title")),
        dueAt = getLong(getColumnIndexOrThrow("due_at")),
        doneAt = getLongOrNull("done_at"),
        wikiId = getStringOrNull("wiki_id"),
        position = getInt(getColumnIndexOrThrow("position")),
    )

    private fun Cursor.toMeasurement(): Measurement = Measurement(
        id = getLong(getColumnIndexOrThrow("id")),
        batchId = getLong(getColumnIndexOrThrow("batch_id")),
        at = getLong(getColumnIndexOrThrow("at")),
        gravity = getDouble(getColumnIndexOrThrow("gravity")),
        temp = getDoubleOrNull("temp"),
        abvCalc = getDouble(getColumnIndexOrThrow("abv_calc")),
        note = getStringOrNull("note"),
    )

    private fun Cursor.toJournal(): JournalEntry = JournalEntry(
        id = getLong(getColumnIndexOrThrow("id")),
        batchId = getLong(getColumnIndexOrThrow("batch_id")),
        at = getLong(getColumnIndexOrThrow("at")),
        kind = JournalKind.fromStorage(getString(getColumnIndexOrThrow("kind"))),
        text = getString(getColumnIndexOrThrow("text")),
        photoUri = getStringOrNull("photo_uri"),
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getDoubleOrNull(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }

    private companion object {
        const val NAME = "user.db"
        const val VERSION = 1
    }
}

data class DueReminder(
    val batchTitle: String,
    val step: BatchStep,
)

