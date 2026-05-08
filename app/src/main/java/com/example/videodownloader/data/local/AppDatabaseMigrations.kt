package com.example.videodownloader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 当前仓库没有保留 v1 结构差异，已知安装基线与 v3 表结构兼容。
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 当前仓库没有保留 v2 结构差异，保留显式迁移入口避免破坏性迁移。
        }
    }
}
