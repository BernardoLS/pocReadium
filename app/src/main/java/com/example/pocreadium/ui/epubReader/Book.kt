package com.example.pocreadium.ui.epubReader

import android.net.Uri
import android.os.Build
import org.readium.r2.shared.Publication
import java.net.URI
import java.nio.file.Paths
import java.util.*

data class Book( var id: Long? = null,
val creation: Long = Date().time,
val href: String,
val title: String,
val author: String? = null,
val identifier: String,
val cover: ByteArray? = null,
val progression: String? = null,
val ext: Publication.EXTENSION
) {

    val fileName: String?
    get() {
        val url = URI(href)
        if (!url.scheme.isNullOrEmpty() && url.isAbsolute) {
            val uri = Uri.parse(href)
            return uri.lastPathSegment
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = Paths.get(href)
            path.fileName.toString()
        } else {
            val uri = Uri.parse(href)
            uri.lastPathSegment
        }
    }

    val url: URI?
    get() {
        val url = URI(href)
        if (url.isAbsolute && url.scheme.isNullOrEmpty()) {
            return null
        }
        return url
    }


}