package com.procurement.point.service

import com.procurement.point.config.OCDSProperties
import com.procurement.point.exception.GetDataException
import com.procurement.point.exception.ParamException
import com.procurement.point.model.dto.PublisherDto
import com.procurement.point.model.dto.offset.CpidDto
import com.procurement.point.model.dto.offset.OffsetDto
import com.procurement.point.model.dto.record.ActualRelease
import com.procurement.point.model.dto.record.Record
import com.procurement.point.model.dto.record.RecordPackage
import com.procurement.point.model.dto.release.ReleasePackageDto
import com.procurement.point.model.entity.OffsetEntity
import com.procurement.point.model.entity.ReleaseEntity
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

    fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackage

    fun getReleasePackage(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto

    fun getRecord(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto

    fun getByOffset(offset: LocalDateTime?, limitParam: Int?): OffsetDto

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

    override fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackage {
        val entities: List<ReleaseEntity>
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

    override fun getReleasePackage(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto {
        val entities: List<ReleaseEntity>
        return if (offset == null) {
            entities = releaseTenderRepository.getAllReleasesByCpIdAndOcId(cpid, ocid)
            when (entities.isNotEmpty()) {
                true -> getReleasePackageDto(entities, cpid, ocid)
                else -> throw GetDataException("No releases found.")
            }
        } else {
            entities = releaseTenderRepository.getAllReleasesByCpIdAndOcIdAndOffset(cpid, ocid, offset.toDate())
            when (entities.isNotEmpty()) {
                true -> getReleasePackageDto(entities, cpid, ocid)
                else -> getEmptyReleasePackageDto()
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

    override fun getByOffset(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset ?: epoch()
        val entities = offsetTenderRepository.getAllByOffset(offsetParam.toDate(), getLimit(limitParam))
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities)
            else -> getEmptyOffsetDto(offsetParam)
        }
    }

    override fun getByOffsetCn(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset ?: epoch()
        val entities = offsetTenderRepository.getAllByOffsetByStatus(
                listOf("active", "cancelled", "unsuccessful", "complete", "withdrawn"),
                offsetParam.toDate(), getLimit(limitParam))
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities)
            else -> getEmptyOffsetDto(offsetParam)
        }
    }

    override fun getByOffsetPlan(offset: LocalDateTime?, limitParam: Int?): OffsetDto {
        val offsetParam = offset ?: epoch()
        val entities = offsetTenderRepository.getAllByOffsetByStatus(
                listOf("planning", "planned"), offsetParam.toDate(),
                getLimit(limitParam))
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities)
            else -> getEmptyOffsetDto(offsetParam)
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

    private fun getRecordPackageDto(entities: List<ReleaseEntity>, cpid: String): RecordPackage {
        val publishedDate = entities.minBy { it.releaseDate }?.releaseDate?.toLocal()
        val records = entities.asSequence().sortedBy { it.releaseDate }
                .map { Record(it.cpId, it.ocId, it.jsonData.toJsonNode()) }.toList()

        val actualReleases = entities.asSequence().filter { it.stage != "MS" && it.status == "active"}
                .map { ActualRelease(stage = it.stage, uri = ocds.path + "tenders/" + it.cpId + "/" + it.ocId) }.toList()

        val recordUrls = records.map { ocds.path + "tenders/" + it.cpid + "/" + it.ocid }
        return RecordPackage(
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

    private fun getReleasePackageDto(entities: List<ReleaseEntity>, cpid: String, ocid: String): ReleasePackageDto {
        val publishedDate = entities.minBy { it.releaseDate }?.releaseDate?.toLocal()
        val releases = entities.asSequence().sortedBy { it.releaseDate }
                .map { it.jsonData.toJsonNode() }.toList()
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

    private fun getOffsetDto(entities: List<OffsetEntity>): OffsetDto {
        val offset = entities.maxBy { it.date }?.date?.toLocal()
        val cpIds = entities.asSequence().sortedBy { it.date }
                .map { CpidDto(it.cpId, it.date.toLocal()) }.toList()
        return OffsetDto(data = cpIds, offset = offset)
    }

    private fun getEmptyOffsetDto(offset: LocalDateTime): OffsetDto {
        return OffsetDto(data = ArrayList(), offset = offset)
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

    private fun getEmptyRecordPackageDto(): RecordPackage {
        return RecordPackage(
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
