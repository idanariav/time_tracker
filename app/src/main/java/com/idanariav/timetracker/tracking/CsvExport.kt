package com.idanariav.timetracker.tracking

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.idanariav.timetracker.domain.buildCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CsvExport {
    /** Writes entries in [from, to) (or everything when null) to a CSV and opens the share sheet. */
    suspend fun share(context: Context, from: Long?, to: Long?, label: String) {
        val file = withContext(Dispatchers.IO) {
            val dao = Graph.dao
            val sessions = if (from != null && to != null) dao.sessionsInOnce(from, to) else dao.allSessions()
            val csv = buildCsv(
                sessions,
                dao.allActivitiesOnce().associateBy { it.id },
                dao.goalsOnce().associateBy { it.id },
                System.currentTimeMillis(),
            )
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            File(dir, "time_tracker_$stamp.csv").apply { writeText(csv) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Time tracker export – $label")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export CSV"))
    }
}
