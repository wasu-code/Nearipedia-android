package io.github.wasu_code.nearipedia

data class WikipediaResponse(
    val query: WikipediaQuery
)

data class WikipediaQuery(
    val geosearch: List<WikipediaArticle>
)