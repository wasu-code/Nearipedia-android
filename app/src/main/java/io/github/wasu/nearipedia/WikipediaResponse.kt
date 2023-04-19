package io.github.wasu.nearipedia

data class WikipediaResponse(
    val query: WikipediaQuery
)

data class WikipediaQuery(
    val geosearch: List<WikipediaArticle>
)