package com.uestra.widgetapp.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface UestraApi {

    /**
     * Sucht Haltestellen via /stops Proxy (Autocomplete).
     */
    @GET("stops")
    suspend fun getAllStops(): List<StationSearchResult>

    /**
     * Holt die Abfahrtszeiten für eine gegebene Haltestelle.
     * Nutzt den exakten Parametersatz der ÜSTRA Webseite, um HTTP 500 Fehler zu vermeiden.
     */
    @GET("proxy2/efa/XML_DM_REQUEST?canChangeMOT=0&coordOutputFormat=WGS84%5Bdd.ddddd%5D&deleteAssignedStops_dm=1&depSequence=30&depType=stopEvents&doNotSearchForStops=1&genMaps=0&imparedOptionsActive=1&inclMOT_1=true&inclMOT_10=true&inclMOT_11=true&inclMOT_13=true&inclMOT_14=true&inclMOT_15=true&inclMOT_16=true&inclMOT_17=true&inclMOT_18=true&inclMOT_19=true&inclMOT_2=true&inclMOT_3=true&inclMOT_4=true&inclMOT_5=true&inclMOT_6=true&inclMOT_7=true&inclMOT_8=true&inclMOT_9=true&includeCompleteStopSeq=1&includedMeans=checkbox&itOptionsActive=1&itdDateTimeDepArr=dep&language=de&locationServerActive=1&maxTimeLoop=1&mergeDep=1&mode=direct&outputFormat=rapidJSON&ptOptionsActive=1&serverInfo=1&sl3plusDMMacro=1&type_dm=any&useAllStops=1&useProxFootSearch=0&useRealtime=1&version=10.5.17.3")
    suspend fun getDepartures(
        @Query("name_dm") stationId: String
    ): UestraDepartureResponse

    companion object {
        private const val BASE_URL = "https://abfahrten.uestra.de/"

        fun create(): UestraApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UestraApi::class.java)
        }
    }
}
