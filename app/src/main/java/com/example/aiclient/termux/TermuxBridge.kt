package com.example.aiclient.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

class TermuxBridge(private val context: Context) {

    companion object {
        private const val TERMUX_PACKAGE = "com.termux"
        private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun executeCommand(
        command: String,
        workdir: String = "~/",
        timeoutMs: Long = 30000,
    ): CommandResult = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<CommandResult>()
        val resultLock = Any()
        var commandResult: CommandResult? = null

        // Build shell script: write command to temp file and execute
        val escapedCmd = command
            .replace("\\", "\\\\")
            .replace("'", "'\\''")
        val script = "cd $workdir && $escapedCmd; echo \"\n__EXIT_CODE__=$?\""

        // Use PendingIntent broadcast to get output
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            Intent("com.example.aiclient.TERMUX_OUTPUT"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val stdout = intent.getStringExtra("stdout") ?: ""
                val stderr = intent.getStringExtra("stderr") ?: ""
                val exitCode = intent.getIntExtra("exit_code", -1)
                synchronized(resultLock) {
                    if (commandResult == null) {
                        commandResult = CommandResult(stdout, stderr, exitCode)
                        deferred.complete(commandResult!!)
                    }
                }
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
            }
        }

        try {
            context.registerReceiver(receiver, IntentFilter("com.example.aiclient.TERMUX_OUTPUT"),
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0)

            val intent = Intent(RUN_COMMAND_ACTION).apply {
                `package` = TERMUX_PACKAGE
                putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGS, arrayOf("-c", script))
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_BACKGROUND, false)
                putExtra(EXTRA_SESSION_ACTION, "0") // new session
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }

            context.startService(intent)
            commandResult = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            }
        } catch (e: Exception) {
            Log.e("TermuxBridge", "Error", e)
            commandResult = CommandResult("", "Error: ${e.message}", -1)
        } finally {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }

        commandResult ?: CommandResult("", "Timeout after ${timeoutMs}ms", -1)
    }

    /**
     * Run command without waiting for output (fire-and-forget).
     */
    fun runCommandNoWait(command: String, workdir: String = "~/") {
        try {
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                `package` = TERMUX_PACKAGE
                putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGS, arrayOf("-c", command))
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_SESSION_ACTION, "0")
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("TermuxBridge", "Error starting command", e)
        }
    }
}
