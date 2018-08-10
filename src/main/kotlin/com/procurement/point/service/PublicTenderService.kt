package com.procurement.point.service

import com.procurement.point.config.OCDSProperties
import com.procurement.point.exception.GetDataException
import com.procurement.point.exception.ParamException
import com.procurement.point.model.dto.PublisherDto
import com.procurement.point.model.dto.DataDto
import com.procurement.point.model.dto.OffsetDto
import com.procurement.point.model.dto.ActualReleaseDto
import com.procurement.point.model.dto.RecordDto
import com.procurement.point.model.dto.RecordPackageDto
import com.procurement.point.model.dto.ReleasePackageDto
import com.procurement.point.model.entity.OffsetTenderEntity
import com.procurement.point.model.entity.ReleaseTenderEntity
import com.procurement.point.repository.OffsetTenderRepository
import com.procurement.point.repository.ReleaseTenderRepository
import com.procurement.point.utils.epoch
import com.procurement.point.utils.toDate
import com.procurement.point.utils.toJsonNode
import com.procurement.point.utils.toLocal
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.LocalDateTime

interface PublicTenderService {

    fun getByOffset(offset: LocalDateTime?, limitParam: Int?): OffsetDto

    fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackageDto

    fun getRecord(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto

    fun getByOffsetCn(offset: LocalDateTime?, limitParam: Int?): OffsetDto

    fun getByOffsetPlan(offset: LocalDateTime?, limitParam: Int?): OffsetDto
}

@Service
@EnableConfigurationProperties(OCDSProperties::class)
class PublicTenderServiceImpl(
        private val releaseTenderRepository: ReleaseTenderRepository,
        private val offsetTenderRepository: OffsetTenderRepository,
        private val ocds: OCDSProperties) : PublicTenderService {

    private val defLimit: Int = ocds.defLimit ?: 100
    private val maxLimit: Int = ocds.maxLimit ?: 300

    override fun getByOffset(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset ?: epoch()
        val entities = offsetTenderRepository.getAllByOffset(offsetParam.toDate())
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities, getLimit(limitParam))
            else -> getEmptyOffsetDto()
        }
    }

    override fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackageDto {
        val entities: List<ReleaseTenderEntity>
        return if (offset == null) {
            entities = releaseTenderRepository.getAllCompiledByCpId(cpid)
            when (entities.isNotEmpty()) {
                true -> getRecordPackageDto(entities, cpid)
                else -> throw GetDataException("No releases found.")
            }
        } else {
            entities = releaseTenderRepository.getAllCompiledByCpIdAndOffset(cpid, offset.toDate())
            when (entities.isNotEmpty()) {
                true -> getRecordPackageDto(entities, cpid)
                else -> getEmptyRecordPackageDto()
            }
        }
    }

    override fun getRecord(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto {
        val entity = releaseTenderRepository.getCompiledByCpIdAndOcid(cpid, ocid)
                ?: throw GetDataException("No releases found.")
        return if (offset != null) {
            if (entity.releaseDate >= offset.toDate()) {
                getReleasePackageDto(listOf(entity), cpid, ocid)
            } else {
                getEmptyReleasePackageDto()
            }
        } else {
            getReleasePackageDto(listOf(entity), cpid, ocid)
        }
    }

    override fun getByOffsetCn(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset?.toDate() ?: epoch().toDate()
        val active = offsetTenderRepository.getAllByOffsetAndStatus("active", offsetParam)
        val cancelled = offsetTenderRepository.getAllByOffsetAndStatus("cancelled", offsetParam)
        val unsuccessful = offsetTenderRepository.getAllByOffsetAndStatus("unsuccessful", offsetParam)
        val complete = offsetTenderRepository.getAllByOffsetAndStatus("complete", offsetParam)
        val withdrawn = offsetTenderRepository.getAllByOffsetAndStatus("withdrawn", offsetParam)
        val entities = active + cancelled + unsuccessful + complete + withdrawn
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities, getLimit(limitParam))
            else -> getEmptyOffsetDto()
        }
    }

    override fun getByOffsetPlan(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset?.toDate() ?: epoch().toDate()
        val planning = offsetTenderRepository.getAllByOffsetAndStatus("planning", offsetParam)
        val planned = offsetTenderRepository.getAllByOffsetAndStatus("planned", offsetParam)
        val entities = planning + planned
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities, getLimit(limitParam))
            else -> getEmptyOffsetDto()
        }
    }

    private fun getLimit(limitParam: Int?): Int {
        return when (limitParam) {
            null -> defLimit
            else -> when {
                limitParam < 0 -> throw ParamException("Limit invalid.")
                limitParam > maxLimit -> maxLimit
                else -> limitParam
            }
        }
    }

    private fun getRecordPackageDto(entities: List<ReleaseTenderEntity>, cpid: String): RecordPackageDto {
        val publishedDate = entities.minBy { it.releaseDate }?.releaseDate?.toLocal()
        val records = entities.asSequence()
                .sortedBy { it.releaseDate }
                .map { RecordDto(it.cpId, it.ocId, it.jsonData.toJsonNode()) }
                .toList()

        val actualReleases = entities.asSequence()
                .filter { it.stage != "MS" && it.status == "active"}
                .map { ActualReleaseDto(stage = it.stage, uri = ocds.path + "tenders/" + it.cpId + "/" + it.ocId) }
                .toList()

        val recordUrls = records.map { ocds.path + "tenders/" + it.cpid + "/" + it.ocid }
        return RecordPackageDto(
                uri = ocds.path + "tenders/" + cpid,
                version = ocds.version,
                extensions = ocds.extensions?.toList(),
                publisher = PublisherDto(
                        name = ocds.publisherName,
                        scheme = ocds.publisherScheme,
                        uid = ocds.publisherUid,
                        uri = ocds.publisherUri),
                license = ocds.license,
                publicationPolicy = ocds.publicationPolicy,
                publishedDate = publishedDate,
                packages = recordUrls,
                records = records,
                actualReleases = actualReleases)
    }

    private fun getReleasePackageDto(entities: List<ReleaseTenderEntity>, cpid: String, ocid: String): ReleasePackageDto {
        val publishedDate = entities.minBy { it.releaseDate }?.releaseDate?.toLocal()
        val releases = entities.asSequence()
                .sortedBy { it.releaseDate }
                .map { it.jsonData.toJsonNode() }
                .toList()
        return ReleasePackageDto(
                uri = ocds.path + "tenders/" + cpid + "/" + ocid,
                version = ocds.version,
                extensions = ocds.extensions?.toList(),
                publisher = PublisherDto(
                        name = ocds.publisherName,
                        scheme = ocds.publisherScheme,
                        uid = ocds.publisherUid,
                        uri = ocds.publisherUri),
                license = ocds.license,
                publicationPolicy = ocds.publicationPolicy,
                publishedDate = publishedDate,
                releases = releases)
    }

    private fun getOffsetDto(entities: List<OffsetTenderEntity>, limit: Int): OffsetDto {
        val offset = entities.maxBy { it.date }?.date?.toLocal()
        val cpIds = entities.asSequence()
                .sortedBy { it.date }
                .map { DataDto(it.cpId, it.date.toLocal()) }
                .take(limit)
                .toList()
        return OffsetDto(data = cpIds, offset = offset)
    }


    private fun getEmptyOffsetDto(): OffsetDto {
        return OffsetDto(data = null, offset = null)
    }

    private fun getEmptyReleasePackageDto(): ReleasePackageDto {
        return ReleasePackageDto(
                uri = null,
                version = null,
                extensions = null,
                publisher = null,
                license = null,
                publicationPolicy = null,
                publishedDate = null,
                releases = null)
    }

    private fun getEmptyRecordPackageDto(): RecordPackageDto {
        return RecordPackageDto(
                uri = null,
                version = null,
                extensions = null,
                publisher = null,
                license = null,
                publicationPolicy = null,
                publishedDate = null,
                packages = null,
                records = null,
                actualReleases = null)
    }
}
