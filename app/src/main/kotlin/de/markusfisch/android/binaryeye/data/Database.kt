package de.markusfisch.android.binaryeye.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.format.DateFormat
import de.markusfisch.android.binaryeye.app.hasNonPrintableCharacters
import de.markusfisch.android.binaryeye.app.prefs

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun getScans(): Cursor? = db.rawQuery(
		"""SELECT
			$SCANS_ID,
			$SCANS_DATETIME,
			$SCANS_CONTENT,
			$SCANS_FORMAT
			FROM $SCANS
			ORDER BY $SCANS_DATETIME DESC
		""", null
	)

	fun getScan(id: Long): Cursor? = db.rawQuery(
		"""SELECT
			$SCANS_DATETIME,
			$SCANS_CONTENT,
			$SCANS_RAW,
			$SCANS_FORMAT
			FROM $SCANS
			WHERE $SCANS_ID = ?
		""", arrayOf("$id")
	)

	fun hasBinaryData(): Cursor? = db.rawQuery(
		"""SELECT
			1
			FROM $SCANS
			WHERE $SCANS_RAW IS NOT NULL
			LIMIT 1
		""", null
	)

	fun insertScan(
		timestamp: Long,
		content: String,
		raw: ByteArray?,
		format: String
	): Long {
		val cv = ContentValues()
		cv.put(
			SCANS_DATETIME, DateFormat.format(
				"yyyy-MM-dd HH:mm:ss",
				timestamp
			).toString()
		)
		val isRaw = hasNonPrintableCharacters(content)
		if (isRaw) {
			cv.put(SCANS_CONTENT, "")
			cv.put(SCANS_RAW, raw ?: content.toByteArray())
		} else {
			cv.put(SCANS_CONTENT, content)
		}
		cv.put(SCANS_FORMAT, format)
		if (prefs.ignoreConsecutiveDuplicates) {
			val id = getLastScan(
				cv.get(SCANS_CONTENT) as String,
				if (isRaw) cv.get(SCANS_RAW) as ByteArray else null,
				format
			)
			if (id > 0L) {
				return id
			}
		}
		return db.insert(SCANS, null, cv)
	}

	private fun getLastScan(
		content: String,
		raw: ByteArray?,
		format: String
	): Long {
		return db.rawQuery(
			"""SELECT
				$SCANS_ID,
				$SCANS_CONTENT,
				$SCANS_RAW,
				$SCANS_FORMAT
				FROM $SCANS
				ORDER BY $SCANS_ID DESC
				LIMIT 1
			""", null
		)?.use {
			if (it.count > 0 &&
				it.moveToFirst() &&
				it.getString(it.getColumnIndex(SCANS_CONTENT)) == content &&
				(raw == null || it.getBlob(it.getColumnIndex(SCANS_RAW))
					?.contentEquals(raw) == true) &&
				it.getString(it.getColumnIndex(SCANS_FORMAT)) == format
			) {
				it.getLong(it.getColumnIndex(SCANS_ID))
			} else {
				0L
			}
		} ?: 0L
	}

	fun removeScan(id: Long) {
		db.delete(SCANS, "$SCANS_ID = ?", arrayOf("$id"))
	}

	fun removeScans() {
		db.delete(SCANS, null, null)
	}

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, "history.db", null, 2) {
		override fun onCreate(db: SQLiteDatabase) {
			createScans(db)
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
			if (oldVersion < 2) {
				addRawColumn(db)
			}
		}
	}

	companion object {
		const val SCANS = "scans"
		const val SCANS_ID = "_id"
		const val SCANS_DATETIME = "_datetime"
		const val SCANS_CONTENT = "content"
		const val SCANS_RAW = "raw"
		const val SCANS_FORMAT = "format"

		private fun createScans(db: SQLiteDatabase) {
			db.execSQL("DROP TABLE IF EXISTS $SCANS")
			db.execSQL(
				"""CREATE TABLE $SCANS (
					$SCANS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
					$SCANS_DATETIME DATETIME NOT NULL,
					$SCANS_CONTENT TEXT NOT NULL,
					$SCANS_RAW BLOB,
					$SCANS_FORMAT TEXT NOT NULL
				)"""
			)
		}

		private fun addRawColumn(db: SQLiteDatabase) {
			db.execSQL("ALTER TABLE $SCANS ADD COLUMN $SCANS_RAW BLOB")
		}
	}
}
