package dev.chungjungsoo.gptmobile.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移。
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) 新增 AI 面具表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ai_masks (
                    mask_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    system_prompt TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_used_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // 2) chats 表新增面具相关字段（可为空）
            db.execSQL("ALTER TABLE chats ADD COLUMN mask_id INTEGER")
            db.execSQL("ALTER TABLE chats ADD COLUMN mask_name TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN system_prompt TEXT")
        }
    }
}
