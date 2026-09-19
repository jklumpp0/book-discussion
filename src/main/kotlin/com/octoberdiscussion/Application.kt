package com.octoberdiscussion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File

@SpringBootApplication
class OctoberDiscussionApplication

fun main(args: Array<String>) {
    // The SQLite JDBC driver does not create the DB file's parent directory itself,
    // and this must run before Spring builds the DataSource bean.
    val dbPath = System.getenv("DB_PATH") ?: "./data/october-discussion.db"
    File(dbPath).absoluteFile.parentFile?.mkdirs()
    runApplication<OctoberDiscussionApplication>(*args)
}
