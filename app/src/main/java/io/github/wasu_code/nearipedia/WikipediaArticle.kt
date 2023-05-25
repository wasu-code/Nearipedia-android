package io.github.wasu_code.nearipedia

data class WikipediaArticle(
    val pageid: Int,
    val title: String,
    val lat: Double,
    val lon: Double,
    val dist: Double,
    val primary: String
)
