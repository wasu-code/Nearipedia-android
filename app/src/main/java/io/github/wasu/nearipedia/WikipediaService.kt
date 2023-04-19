package io.github.wasu.nearipedia

import retrofit2.http.GET
import retrofit2.http.Query

interface WikipediaService {
    @GET("w/api.php?action=query&format=json&list=geosearch&gsradius=10000&gslimit=10")
    suspend fun getNearbyArticles(
        @Query("gscoord") coordinates: String
    ): WikipediaResponse
}