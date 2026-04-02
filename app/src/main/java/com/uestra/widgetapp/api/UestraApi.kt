package com.uestra.widgetapp.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** API for GVH Hannover departures (HAFAS mgate.exe). Native precision. */
interface UestraApi {

    @POST("bin/mgate.exe")
    suspend fun getDepartures(
        @Body request: HafasRequest
    ): HafasResponse

    companion object {
        private const val BASE_URL = "https://gvh.hafas.de/"

        fun create(): UestraApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UestraApi::class.java)
        }
    }
}
