package com.jianji.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 数据库迁移测试。
 *
 * 当前为 1.0 版本：校验导出的 schema 与实际建表一致。
 * 后续每次升级都必须在此追加对应版本的校验用例：
 *   helper.runMigrationsAndValidate(TEST_DB, N, true, MIGRATION_1_TO_2, ...)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JianjiDatabase::class.java,
    )

    @Test
    fun `schema export exists for current version`() {
        val schemaFile = File(
            "schemas/${JianjiDatabase::class.java.name}/${JianjiDatabase.VERSION_1}.json",
        )
        assertTrue(
            "schema JSON 未导出，请检查 ksp room.schemaLocation 配置: ${schemaFile.absolutePath}",
            schemaFile.exists(),
        )
    }

    @Test
    fun `validate version 1 schema`() {
        // Room 2.7 起需先按 schema 建库，再执行迁移并校验
        helper.createDatabase(TEST_DB, JianjiDatabase.VERSION_1)
        helper.runMigrationsAndValidate(TEST_DB, JianjiDatabase.VERSION_1, true)
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
