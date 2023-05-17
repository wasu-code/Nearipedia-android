package io.github.wasu.nearipedia

import retrofit2.http.GET
import retrofit2.http.Query

interface WikipediaService {
    @GET("w/api.php?action=query&format=json&list=geosearch")
    suspend fun getNearbyArticles(
        @Query("gscoord") coordinates: String,
        @Query("gsradius") radius: Int = 10000,
        @Query("gslimit") limit: Int = 1000
    ): WikipediaResponse
}