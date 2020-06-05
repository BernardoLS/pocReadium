package com.example.pocreadium.ui.main

import android.app.ProgressDialog
import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import com.example.pocreadium.BuildConfig.DEBUG
import com.example.pocreadium.R
import com.example.pocreadium.ui.epubReader.Book
import com.example.pocreadium.ui.epubReader.EpubReaderActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import org.jetbrains.anko.indeterminateProgressDialog
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.parser.epub.EPUBConstant
import org.readium.r2.streamer.parser.epub.EpubParser
import org.readium.r2.streamer.server.Server
import org.zeroturnaround.zip.commons.IOUtils
import timber.log.Timber
import java.io.*
import java.net.ServerSocket
import java.net.URL
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

        button_open_epub.setOnClickListener { buttonClick() }
        button_add_epub.setOnClickListener { showDocumentPicker() }
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
            href = "/data/user/0/com.example.pocreadium/files/346e5abd-abb1-49ed-b72e-caff04e70be7",
            ext = Publication.EXTENSION.EPUB,
            identifier = "9789722042888",
            title = "O Planalto e a Estepe",
            progression = null
        )
        val publicationPath = R2DIRECTORY + book.fileName
        println("---- $R2DIRECTORY")
        println("---- ${book.fileName}")
        val file = File(book.href)
        val parser = EpubParser()
        println("---- $parser")
        val pub = parser.parse(publicationPath)
        println("---- $pub")
        pub?.let {
            println("---- publication${it.publication}")
            prepareAndStartActivity(pub, book, file, publicationPath, pub.publication)
        }
    }

    private fun prepareAndStartActivity(pub: PubBox?, book: Book, file: File, publicationPath: String, publication: Publication) {
        prepareToServe(pub, book.fileName!!, file.absolutePath, add = false, lcp = false)
        println("ward -- ${pub}")
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

    private fun showDocumentPicker() {
        // ACTION_GET_DOCUMENT allows to import a system file by creating a copy of it
        // with access to every app that manages files
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // Filter to show only epubs, using the image MIME data type.
        // To search for all documents available via installed storage providers,
        // it would be "*/*".
        intent.type = "*/*"
//        val mimeTypes = arrayOf(
//                "application/epub+zip",
//                "application/x-cbz"
//        )
//        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data ?: return
        // The document selected by the user won't be returned in the intent.
        // Instead, a URI to that document will be contained in the return intent
        // provided to this method as a parameter.
        // Pull that URI using resultData.getData().
        if (requestCode == 1 && resultCode == RESULT_OK) {
            //val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
            task {
              //  progress.show()
                println("---- progress")
            } then {
                val uri: Uri? = data.data
                uri?.let {
                    val fileType = getMimeType(uri)
                    val mime = fileType.first
                    val name = fileType.second
                    if (name.endsWith(Publication.EXTENSION.LCPL.value)) {
                       // processLcpActivityResult(uri, it, progress, isNetworkAvailable)
                    } else {
                        processEpubResult(uri, mime, name)
                    }
                }
            }
        } else if (resultCode == RESULT_OK) {
           // val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
            //progress.show()
            task {
                val filePath = data.getStringExtra("resultPath")
                parseIntent(filePath)
            } then {
                //progress.dismiss()
            }
        }
    }

    fun prepareToServe(pub: PubBox?, fileName: String, absolutePath: String, add: Boolean, lcp: Boolean) {
        if (pub == null) {
           // catalogView.snackbar("Invalid publication")
            return
        }
        println("---- fileName: $fileName")
        val publication = pub.publication
        println("---- publication${publication}")

        val container = pub.container
        launch {
            val publicationIdentifier = publication.metadata.identifier!!
            preferences.edit().putString("$publicationIdentifier-publicationPort", localPort.toString()).apply()
            server.addEpub(publication, container, "/$fileName", applicationContext.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json")
        }
    }

    private fun parseIntent(filePath: String?) {
        filePath?.let {
            //val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
            //progress.show()
            val fileName = UUID.randomUUID().toString()
            val publicationPath = R2DIRECTORY + fileName
            task {
                copyFile(File(filePath), File(publicationPath))
            } then {
                preparePublication(publicationPath, filePath, fileName)
            }
        } ?: run {
            val intent = intent
            val uriString: String? = intent.getStringExtra("URI")
            uriString?.let {
                parseIntentPublication(uriString)
            }
        }
    }

    fun copyFile(src: File, dst: File) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = FileInputStream(src)
            out = FileOutputStream(dst)
            IOUtils.copy(`in`, out)
        } catch (ioe: IOException) {
            if (DEBUG) Timber.e(ioe)
        } finally {
            IOUtils.closeQuietly(out)
            IOUtils.closeQuietly(`in`)
        }
    }

    private fun preparePublication(publicationPath: String, uriString: String, fileName: String) {
        val file = File(publicationPath)
        try {
            launch {
                when {
                    uriString.endsWith(Publication.EXTENSION.EPUB.value) -> {
                        val parser = EpubParser()
                        val pub = parser.parse(publicationPath)
                        if (pub != null) {
                            prepareToServe(pub, fileName, file.absolutePath, add = true, lcp = pub.container.drm?.let { true }
                                ?: false)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun parseIntentPublication(uriString: String) {
        val uri: Uri? = Uri.parse(uriString)
        if (uri != null) {
           // val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
          //  progress.show()
            val fileName = UUID.randomUUID().toString()
            val publicationPath = R2DIRECTORY + fileName
            task {
                getContentInputStream(this, uri, publicationPath)
            } then {
                preparePublication(publicationPath, uriString, fileName)
            }
        }
    }

    fun getContentInputStream(context: Context, uri: Uri, publicationPath: String) {
        try {
            val path = getRealPath(context, uri)
            if (path != null) {
                copyFile(File(path), File(publicationPath))
            } else {
                val input = URL(uri.toString()).openStream()
                input.toFile(publicationPath)
            }
        } catch (e: Exception) {
//            val input = getInputStream(context, uri)
//            input?.let {
//                input.toFile(publicationPath)
//            }
            println("---- catch getContentInputStream")
        }
    }

    private fun getMimeType(uri: Uri): Pair<String, String> {
        val mimeType: String?
        var fileName = String()
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val contentResolver: ContentResolver = applicationContext.contentResolver
            mimeType = contentResolver.getType(uri)
            getContentName(contentResolver, uri)?.let {
                fileName = it
            }
        } else {
            val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(uri
                .toString())
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                fileExtension.toLowerCase())
        }
        return Pair(mimeType!!, fileName)
    }

    private fun processEpubResult(uri: Uri?, mime: String, name: String) {
        val fileName = UUID.randomUUID().toString()
        val publicationPath = R2DIRECTORY + fileName
        val input = contentResolver.openInputStream(uri as Uri)
        launch {
            input?.toFile(publicationPath)
            val file = File(publicationPath)
            try {
                if (mime == EPUBConstant.mimetype) {
                    val parser = EpubParser()
                    val pub = parser.parse(publicationPath)
                    if (pub != null) {
                        prepareToServe(pub, fileName, file.absolutePath, add = true, lcp = pub.container.drm?.let { true }
                            ?: false)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun getContentName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(uri, null, null, null, null)
        cursor!!.moveToFirst()
        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        return if (nameIndex >= 0) {
            val name = cursor.getString(nameIndex)
            cursor.close()
            name
        } else {
            null
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main


    fun InputStream.toFile(path: String) {
        use { input ->
            File(path).outputStream().use { input.copyTo(it) }
        }
    }

    private fun getRealPath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
                // TODO handle non-primary volumes

            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                if (!TextUtils.isEmpty(id)) {
                    if (id.startsWith("raw:")) {
                        return id.replaceFirst("raw:".toRegex(), "")
                    }
                    return try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                        getDataColumn(context, contentUri, null, null)
                    } catch (e: NumberFormatException) {
                        null
                    }

                }
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme!!, ignoreCase = true)) {

            // Return the remote address
            return getDataColumn(context, uri, null, null)

        } else if ("file".equals(uri.scheme!!, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?,
                      selectionArgs: Array<String>?): String? {

        val column = "_data"
        val projection = arrayOf(column)
        context.contentResolver.query(uri!!, projection, selection, selectionArgs, null).use { cursor ->
            cursor?.let {
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(index)
                }
            }
        }
        return null
    }
}