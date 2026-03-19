package dev.chungjungsoo.gptmobile.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移。
 */
object Migrations {
    private const val DEFAULT_ROLE_NAME = "AI助手"
    private const val DEFAULT_ROLE_GROUP = "默认"
    private const val UNGROUPED_ROLE_NAME = "未分组"

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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN model_name TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_masks ADD COLUMN group_name TEXT NOT NULL DEFAULT '$UNGROUPED_ROLE_NAME'")
            db.execSQL("ALTER TABLE ai_masks ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_masks ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chats ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                INSERT INTO ai_masks (name, system_prompt, group_name, is_default, is_archived, updated_at, last_used_at)
                SELECT '$DEFAULT_ROLE_NAME', '', '$DEFAULT_ROLE_GROUP', 1, 0, CAST(strftime('%s', 'now') AS INTEGER), 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM ai_masks WHERE is_default = 1 OR name = '$DEFAULT_ROLE_NAME'
                )
                """.trimIndent()
            )
        }
    }
}
