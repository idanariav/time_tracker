package com.idanariav.timetracker.link

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * URI scheme used by NFC tags, launcher shortcuts and automation apps (Tasker etc.):
 *  ttrack://activity/<id>            switch to the activity
 *  ttrack://activity/<id>?toggle=1   stop it if it's running, otherwise switch to it
 *  ttrack://stop                     stop tracking
 */
object Links {
    const val SCHEME = "ttrack"
    fun activity(id: Long, toggle: Boolean = false) = "$SCHEME://activity/$id" + if (toggle) "?toggle=1" else ""
}

/** Invisible activity: performs the link action, shows a toast, and gets out of the way. */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        finish()
        if (uri == null || uri.scheme != Links.SCHEME) return
        val app = applicationContext
        Graph.appScope.launch {
            val msg = runCatching { handle(uri) }.getOrElse { "Time tracker: ${it.message}" }
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun handle(uri: Uri): String = when (uri.host) {
        "stop" -> if (Graph.tracker.stop() != null) "Tracking stopped" else "Nothing was running"
        "activity" -> {
            val a = uri.pathSegments.firstOrNull()?.toLongOrNull()?.let { Graph.dao.activity(it) }
            when {
                a == null -> "Unknown activity (was it deleted?)"
                uri.getQueryParameter("toggle") == "1" ->
                    if (Graph.tracker.toggle(a.id)) "${a.emoji} ${a.name} started" else "${a.emoji} ${a.name} stopped"
                Graph.tracker.switchTo(a.id) == null -> "${a.emoji} ${a.name} already running"
                else -> "${a.emoji} ${a.name} started"
            }
        }
        else -> "Unknown link"
    }
}
