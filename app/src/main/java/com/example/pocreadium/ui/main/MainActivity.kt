package com.example.pocreadium.ui.main

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.SharedPreferences
import com.example.pocreadium.BuildConfig.DEBUG
import com.example.pocreadium.R
import com.example.pocreadium.ui.epubReader.Book
import com.example.pocreadium.ui.epubReader.EpubReaderActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.parser.epub.EpubParser
import org.readium.r2.streamer.server.Server
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity() : AppCompatActivity(), CoroutineScope {

    private lateinit var R2DIRECTORY: String
    private lateinit var server: Server
    private var localPort: Int = 0
    private lateinit var preferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)

        val socket = ServerSocket(0)
        socket.localPort
        socket.close()

        localPort = socket.localPort
        server = Server(localPort)

        val properties = Properties()
        val inputStream = this.assets.open("configs/config.properties")
        properties.load(inputStream)
        val useExternalFileDir = properties.getProperty("useExternalFileDir", "false")!!.toBoolean()

        R2DIRECTORY = if (useExternalFileDir) {
            this.getExternalFilesDir(null)?.path + "/"
        } else {
            this.filesDir.path + "/"
        }

        buttonClick()
    }

    override fun onStart() {
        super.onStart()
        startServer()
//        permissionHelper.storagePermission {
//            if (books.isEmpty()) {
//                if (!preferences.contains("samples")) {
//                    val dir = File(R2DIRECTORY)
//                    if (!dir.exists()) {
//                        dir.mkdirs()
//                    }
//                    copySamplesFromAssetsToStorage()
//                    preferences.edit().putBoolean("samples", true).apply()
//                }
//            }
//}
        }


    private fun startServer() {
        if (!server.isAlive) {
            try {
                server.start()
            } catch (e: IOException) {
                // do nothing
                //if (DEBUG) Timber.e(e)
            }
            if (server.isAlive) {
                // Add Resources from R2Navigator
                server.loadReadiumCSSResources(assets)
                server.loadR2ScriptResources(assets)
                server.loadR2FontResources(assets, applicationContext)
                server.loadCustomResource(assets.open("Search/mark.js"), "mark.js", Injectable.Script)
                server.loadCustomResource(assets.open("Search/search.js"), "search.js", Injectable.Script)
                server.loadCustomResource(assets.open("Search/mark.css"), "mark.css", Injectable.Style)
                server.loadCustomResource(assets.open("scripts/crypto-sha256.js"), "crypto-sha256.js", Injectable.Script)
                server.loadCustomResource(assets.open("scripts/highlight.js"), "highlight.js", Injectable.Script)
            }
        }
    }

    private fun buttonClick() {
        val book = Book(
            id = 0,
            author = "Pepetela",
            cover = null,
            creation = 1591292956908,
            href = "/data/user/0/com.example.pocreadium/files/2.pub",
            ext = Publication.EXTENSION.EPUB,
            identifier = "9789722042888",
            title = "O Planalto e a Estepe",
            progression = null
        )
        val publicationPath = R2DIRECTORY + book.fileName
        val file = File(book.href)
        val parser = EpubParser()
        val pub = parser.parse(publicationPath)
        pub?.let {
            prepareAndStartActivity(pub, book, file, publicationPath, pub.publication)
        }
    }


    private fun prepareAndStartActivity(pub: PubBox?, book: Book, file: File, publicationPath: String, publication: Publication) {
        prepareToServe(pub, book.fileName!!, file.absolutePath, add = false, lcp = false)
        startActivity(publicationPath, book, publication)
    }
    private fun startActivity(publicationPath: String, book: Book, publication: Publication, coverByteArray: ByteArray? = null) {
        val intent = Intent(this, EpubReaderActivity::class.java)
        intent.putExtra("publicationPath", publicationPath)
        intent.putExtra("publicationFileName", book.fileName)
        intent.putExtra("publication", publication)
        intent.putExtra("bookId", book.id)
        intent.putExtra("cover", coverByteArray)
        startActivity(intent)
    }

    fun prepareToServe(pub: PubBox?, fileName: String, absolutePath: String, add: Boolean, lcp: Boolean) {
        if (pub == null) {
           // catalogView.snackbar("Invalid publication")
            return
        }
        val publication = pub.publication
        val container = pub.container
        launch {
            val publicationIdentifier = publication.metadata.identifier!!
            preferences.edit().putString("$publicationIdentifier-publicationPort", localPort.toString()).apply()
            server.addEpub(publication, container, "/$fileName", applicationContext.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json")
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

}