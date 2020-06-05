package com.example.pocreadium.ui.epubReader

import android.os.Bundle
import com.example.pocreadium.R
import org.readium.r2.navigator.epub.R2EpubActivity


class EpubReaderActivity : R2EpubActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epub_reader)

        resourcePager.offscreenPageLimit = 1
        currentPagerPosition = publication.readingOrder.indexOfFirst { it.href == currentLocation?.href }
        resourcePager.currentItem = currentPagerPosition

    }


}