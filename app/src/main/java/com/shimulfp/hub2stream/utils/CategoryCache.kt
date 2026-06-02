package com.shimulfp.hub2stream.utils

import com.shimulfp.hub2stream.extractor.models.MediaItemPreview

object CategoryCache {
    var currentItems: List<MediaItemPreview> = emptyList()
    var categoryType: String? = null
    var categoryData: String? = null
    var currentTitle: String? = null
}