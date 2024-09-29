package jooqutils.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import mu.KotlinLogging

object ShellRunner {

    private val logger = KotlinLogging.logger {}

    data class CommandResult(val result: Int, val lines: List<String>)

    internal class StreamConsumer(val inputStream: InputStream, val lines: MutableList<String>) :
        Runnable {
        override fun run() {
            BufferedReader(InputStreamReader(inputStream)).lines().forEach { lines.add(it) }
        }
    }

    fun run(command: String, vararg params: String): CommandResult = doRun(null, command, *params)

    fun run(directory: Path, command: String, vararg params: String): CommandResult =
        doRun(directory, command, *params)

    private fun doRun(directory: Path?, command: String, vararg params: String): CommandResult {
        val builder = ProcessBuilder()
        if (directory != null) {
            builder.directory(directory.toFile())
        }
        builder.environment().apply {
            put("PATH", "${get("PATH")}:/usr/local/bin")
        }
        val fullCommand = command + params.fold("") { acc, s -> "$acc $s" }
        logger.debug { "Run '$fullCommand'" }
        builder.command("sh", "-c", fullCommand)
        val process = builder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val lines = mutableListOf<String>()
        do {
            val line = reader.readLine()
            if (line != null) lines.add(line)
        } while (line != null)
        val result = process.waitFor()
        logger.debug { "Command result : $result" }
        return CommandResult(result, lines)
    }
}
