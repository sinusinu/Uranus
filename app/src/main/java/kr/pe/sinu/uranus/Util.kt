// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

class Util {
    companion object {
        // handling file rw in java sucks
        @JvmStatic
        fun readString(file: File): String {
            return file.readText()
        }
        @JvmStatic
        fun writeString(file: File, content: String) {
            file.writeText(content)
        }

        @JvmStatic
        fun getFileExt(filename: String?): String {
            if (filename == null) return ""
            val dotPos = filename.lastIndexOf('.')
            return if (dotPos == -1) "" else filename.substring(dotPos + 1).lowercase(Locale.getDefault())
        }

        @JvmStatic
        fun stripFileExt(filename: String?): String {
            if (filename == null) return ""
            val dotPos = filename.lastIndexOf('.')
            if (dotPos == -1) {
                return filename
            } else {
                val ext = filename.substring(dotPos + 1).lowercase()
                return if (LibraryActivity.ALLOWED_EXTENSIONS.any { allowedExt -> ext == allowedExt }) {
                    filename.substring(0, dotPos)
                } else {
                    filename
                }
            }
        }

        @JvmStatic
        fun getFilenameFromUri(context: Context, uri: Uri): String {
            var name: String = ""
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor == null) return ""
                while (cursor.moveToNext()) {
                    name = cursor.getString(0) ?: ""
                }
            }
            return name
        }

        @JvmStatic
        fun toTimestamp(seconds: Long): String {
            if (seconds < 60) return $"0:${seconds.toString().padStart(2, '0')}"
            val minutes = seconds / 60
            val modSeconds = seconds % 60
            if (minutes < 60) return $"$minutes:${modSeconds.toString().padStart(2, '0')}"
            val hours = minutes / 60
            val modMinutes = minutes % 60
            return $"$hours:${modMinutes.toString().padStart(2, '0')}:${modSeconds.toString().padStart(2, '0')}"
        }

        @JvmStatic
        fun toCrc32(input: String): String {
            val crc = CRC32().apply { update(input.toByteArray(Charsets.UTF_8)) }
            return "%08x".format(crc.value)
        }

        @JvmStatic
        fun toMd5(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}