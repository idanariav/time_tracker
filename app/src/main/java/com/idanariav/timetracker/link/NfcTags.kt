package com.idanariav.timetracker.link

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

object NfcTags {
    /** Puts the adapter in reader mode; the next tag that touches the phone gets [uri] written to it. */
    fun startWriting(activity: Activity, adapter: NfcAdapter, uri: String, onResult: (Boolean, String) -> Unit) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, { tag ->
            val r = write(tag, uri, activity.packageName)
            activity.runOnUiThread { onResult(r.isSuccess, r.getOrElse { it.message ?: "Write failed" }) }
        }, flags, null)
    }

    fun stop(activity: Activity, adapter: NfcAdapter?) {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    private fun write(tag: Tag, uri: String, pkg: String): Result<String> = runCatching {
        // URI record drives the action; the Android Application Record makes sure our app handles it.
        val msg = NdefMessage(arrayOf(NdefRecord.createUri(uri), NdefRecord.createApplicationRecord(pkg)))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                require(it.isWritable) { "This tag is read-only." }
                require(it.maxSize >= msg.byteArrayLength) { "Tag too small (${it.maxSize} bytes)." }
                it.writeNdefMessage(msg)
            }
        } else {
            val f = NdefFormatable.get(tag) ?: error("This tag type isn't supported (needs NDEF).")
            f.use {
                it.connect()
                it.format(msg)
            }
        }
        "Tag written ✓ — tap it any time to toggle this activity."
    }
}
