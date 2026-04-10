package com.uestra.widgetapp.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.http.GET
import retrofit2.http.Query

/** API for GVH Hannover via ÜSTRA Web Proxy. */
interface UestraApi {
    /** 
     * Fetch departures via ÜSTRA Web Proxy (Original EFA).
     * Uses the exact parameter set from the ÜSTRA website to ensure successful rapidJSON output.
     */
    @GET("proxy2/efa/XML_DM_REQUEST?canChangeMOT=0&coordOutputFormat=WGS84%5Bdd.ddddd%5D&deleteAssignedStops_dm=1&depSequence=30&depType=stopEvents&doNotSearchForStops=1&genMaps=0&imparedOptionsActive=1&inclMOT_1=true&inclMOT_10=true&inclMOT_11=true&inclMOT_13=true&inclMOT_14=true&inclMOT_15=true&inclMOT_16=true&inclMOT_17=true&inclMOT_18=true&inclMOT_19=true&inclMOT_2=true&inclMOT_3=true&inclMOT_4=true&inclMOT_5=true&inclMOT_6=true&inclMOT_7=true&inclMOT_8=true&inclMOT_9=true&includeCompleteStopSeq=1&includedMeans=checkbox&itOptionsActive=1&itdDateTimeDepArr=dep&language=de&locationServerActive=1&maxTimeLoop=1&mergeDep=1&mode=direct&outputFormat=rapidJSON&ptOptionsActive=1&serverInfo=1&sl3plusDMMacro=1&type_dm=any&useAllStops=1&useProxFootSearch=0&useRealtime=1&version=10.5.17.3&c=18")
    suspend fun getDepartures(
        @Query("name_dm") stationId: String
    ): UestraDepartureResponse

    /** Fetch all stops from the ÜSTRA web proxy */
    @GET("stops")
    suspend fun getAllStops(): List<StationSearchResult>

    companion object {
        private const val WEB_BASE_URL = "https://abfahrten.uestra.de/"

        fun create(): UestraApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(WEB_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UestraApi::class.java)
        }
    }
}
