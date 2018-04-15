package com.procurement.point.model.dto.record

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.procurement.point.databinding.JsonDateSerializer
import com.procurement.point.model.dto.PublisherDto
import java.time.LocalDateTime

data class RecordPackageDto(

        @JsonProperty("uri")
        val uri: String?,

        @JsonProperty("version")
        val version: String?,

        @JsonProperty("extensions")
        val extensions: List<String>?,

        @JsonProperty("publisher")
        val publisher: PublisherDto?,

        @JsonProperty("license")
        val license: String?,

        @JsonProperty("publicationPolicy")
        val publicationPolicy: String?,

        @JsonProperty("publishedDate")
        @JsonSerialize(using = JsonDateSerializer::class)
        val publishedDate: LocalDateTime?,

        @JsonProperty("packages")
        val packages: List<String>?,

        @JsonProperty("records")
        val records: List<RecordDto>?
)