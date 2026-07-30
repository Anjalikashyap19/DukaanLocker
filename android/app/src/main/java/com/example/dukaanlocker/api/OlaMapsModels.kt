package com.example.dukaanlocker.api

import com.google.gson.annotations.SerializedName

data class OlaAutocompleteResponse(
    @SerializedName("predictions") val predictions: List<OlaPrediction>?,
    @SerializedName("status") val status: String
)

data class OlaPrediction(
    @SerializedName("place_id") val placeId: String,
    @SerializedName("description") val description: String,
    @SerializedName("structured_formatting") val structuredFormatting: OlaStructuredFormatting?
)

data class OlaStructuredFormatting(
    @SerializedName("main_text") val mainText: String,
    @SerializedName("secondary_text") val secondaryText: String
)
