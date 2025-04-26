package im.brodriro.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>)

@Serializable
data class GeocodingResult(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    @SerialName("feature_code")
    val featureCode: String? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("admin1_id")
    val admin1Id: Long? = null,
    @SerialName("admin3_id")
    val admin3Id: Long? = null,
    val timezone: String? = null,
    val population: Long? = null,
    @SerialName("country_id")
    val countryId: Long? = null,
    val country: String? = null,
    val admin1: String? = "",
    val admin3: String? = ""
)