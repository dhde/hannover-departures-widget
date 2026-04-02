package com.uestra.widgetapp.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** API for GVH Hannover. Hybrid: HAFAS for departures, Web-Proxy for stops. */
interface UestraApi {

    /** Fetch departures via HAFAS mgate.exe */
    @POST("bin/mgate.exe")
    suspend fun getDepartures(
        @Body request: HafasRequest
    ): HafasResponse

    /** Fetch all stops from the ÜSTRA web proxy */
    @GET("stops")
    suspend fun getAllStops(): List<StationSearchResult>

    companion object {
        private const val BASE_URL = "https://gvh.hafas.de/"
        private const val WEB_BASE_URL = "https://abfahrten.uestra.de/"

        fun create(): UestraApi {
            // By default, we use the HAFAS base URL
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UestraApi::class.java)
        }
        
        fun createWeb(): UestraApi {
            // For the /stops endpoint on the web server
            val retrofit = Retrofit.Builder()
                .baseUrl(WEB_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UestraApi::class.java)
        }
    }
}
