package com.uestra.widgetapp.api

import com.google.gson.annotations.SerializedName

/** Request models for HAFAS mgate.exe */
data class HafasRequest(
    val svcReqL: List<SvcReq>,
    val auth: HafasAuth = HafasAuth(),
    val client: HafasClient = HafasClient(),
    val ver: String = "1.16",
    val lang: String = "de"
)

data class SvcReq(
    val meth: String,
    val req: StationBoardReq
)

data class StationBoardReq(
    val type: String = "DEP",
    val stbLoc: StbLoc,
    val maxJny: Int = 20,
    val date: String? = null,
    val time: String? = null
)

data class StbLoc(
    val lid: String
)

data class HafasAuth(
    val aid: String = "gvh-app",
    val type: String = "AID"
)

data class HafasClient(
    val id: String = "GVH",
    val type: String = "IPH",
    val v: String = "5000400"
)

/** Response models for HAFAS mgate.exe */
data class HafasResponse(
    val svcResL: List<SvcRes>?
)

data class SvcRes(
    val meth: String,
    val res: StationBoardRes?
)

data class StationBoardRes(
    val jnyL: List<HafasJourney>?
)

data class HafasJourney(
    val stp: HafasStop?,
    val date: String?,
    val time: String?,
    @SerializedName("rtTime") val rtTime: String?,
    @SerializedName("rtDate") val rtDate: String?,
    @SerializedName("dirTxt") val direction: String?,
    @SerializedName("prodL") val product: List<HafasProduct>?
)

data class HafasStop(
    val loc: HafasLocation?
)

data class HafasLocation(
    val name: String?,
    val lid: String?
)

data class HafasProduct(
    @SerializedName("name") val name: String?,
    @SerializedName("line") val line: String?,
    @SerializedName("catOut") val category: String?
)
