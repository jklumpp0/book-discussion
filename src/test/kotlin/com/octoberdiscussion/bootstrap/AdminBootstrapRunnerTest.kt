package com.octoberdiscussion.bootstrap

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.sqlite.JDBC
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminBootstrapRunnerTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var runner: AdminBootstrapRunner

    @BeforeEach
    fun setUp() {
        val dbFile = tempDir.resolve("test.db")
        val dataSource = SimpleDriverDataSource(JDBC(), "jdbc:sqlite:$dbFile")
        jdbcTemplate = JdbcTemplate(dataSource)

        val schemaSql =
            checkNotNull(javaClass.getResourceAsStream("/schema.sql")) { "schema.sql not found on classpath" }
                .bufferedReader()
                .readText()
        schemaSql
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { jdbcTemplate.execute(it) }

        runner = AdminBootstrapRunner(jdbcTemplate)
    }

    @Test
    fun `seeds exactly one admin user on first run and never reseeds`() {
        runner.run(DefaultApplicationArguments())

        val afterFirstRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Int::class.java)
        assertEquals(1, afterFirstRun)

        val admin = jdbcTemplate.queryForMap("SELECT display_name, role, access_code_hash FROM user")
        assertEquals("Admin", admin["display_name"])
        assertEquals("admin", admin["role"])
        assertTrue((admin["access_code_hash"] as String).startsWith("\$2"))

        runner.run(DefaultApplicationArguments())

        val afterSecondRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Int::class.java)
        assertEquals(1, afterSecondRun)
    }
}
