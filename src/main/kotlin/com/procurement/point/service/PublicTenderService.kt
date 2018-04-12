package com.procurement.point.service

import com.procurement.point.config.properties.OCDSProperties
import com.procurement.point.exception.GetDataException
import com.procurement.point.model.dto.PublisherDto
import com.procurement.point.model.dto.offset.CpidDto
import com.procurement.point.model.dto.offset.OffsetDto
import com.procurement.point.model.dto.record.RecordDto
import com.procurement.point.model.dto.record.RecordPackageDto
import com.procurement.point.model.dto.release.ReleasePackageDto
import com.procurement.point.model.entity.OffsetEntity
import com.procurement.point.model.entity.ReleaseEntity
import com.procurement.point.repository.OffsetTenderRepository
import com.procurement.point.repository.ReleaseTenderRepository
import com.procurement.point.utils.toDate
import com.procurement.point.utils.toJsonNode
import com.procurement.point.utils.toLocal
import org.springframework.stereotype.Service
import java.time.LocalDateTime

interface PublicTenderService {

    fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackageDto

    fun getReleasePackage(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto

    fun getByOffset(offset: LocalDateTime, limit: Int): OffsetDto
}

@Service
class PublicTenderServiceImpl(
        private val releaseTenderRepository: ReleaseTenderRepository,
        private val offsetTenderRepository: OffsetTenderRepository,
        private val ocds: OCDSProperties) : PublicTenderService {

    override fun getRecordPackage(cpid: String, offset: LocalDateTime?): RecordPackageDto {
        val entities = when (offset) {
            null -> releaseTenderRepository.getAllByCpId(cpid)
            else -> releaseTenderRepository.getAllByCpIdAndOffset(cpid, offset.toDate())
        }
        when (!entities.isEmpty()) {
            true -> return getRecordPackageDto(entities, cpid)
            else -> throw GetDataException("No records found.")
        }
    }

    override fun getReleasePackage(cpid: String, ocid: String, offset: LocalDateTime?): ReleasePackageDto {
        val entities = when (offset) {
            null -> releaseTenderRepository.getAllByCpIdAndOcId(cpid, ocid)
            else -> releaseTenderRepository.getAllByCpIdAndOcIdAndOffset(cpid, ocid, offset.toDate())
        }
        when (!entities.isEmpty()) {
            true -> return getReleasePackageDto(entities, cpid)
            else -> throw GetDataException("No releases found.")
        }
    }

    override fun getByOffset(offset: LocalDateTime, limit: Int): OffsetDto {
        val entities = offsetTenderRepository.getAllByOffset(offset.toDate(), limit)
        return when (!entities.isEmpty()) {
            true -> getOffsetDto(entities)
            else -> getEmptyOffsetDto(offset)
        }
    }

    private fun getRecordPackageDto(entities: List<ReleaseEntity>, cpid: String): RecordPackageDto {
        val publishedDate = entities.maxBy { it.releaseDate }?.releaseDate?.toLocal()
        val records = entities.asSequence().sortedByDescending { it.releaseDate }
                .map { RecordDto(it.ocId, it.jsonData.toJsonNode()) }.toList()
        val recordUrls = records.map { ocds.path + it.ocid }
        return RecordPackageDto(
                uri = ocds.path + cpid,
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
                records = records)
    }

    private fun getReleasePackageDto(entities: List<ReleaseEntity>, cpid: String): ReleasePackageDto {
        val publishedDate = entities.maxBy { it.releaseDate }?.releaseDate?.toLocal()
        val releases = entities.asSequence().sortedByDescending { it.releaseDate }
                .map { it.jsonData.toJsonNode() }.toList()
        return ReleasePackageDto(
                uri = ocds.path + cpid,
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
        val cpIds = entities.asSequence().sortedByDescending { it.date }
                .map { CpidDto(it.cpId, it.date.toLocal()) }.toList()
        return OffsetDto(data = cpIds, offset = offset)
    }

    private fun getEmptyOffsetDto(offset: LocalDateTime): OffsetDto {
        return OffsetDto(data = ArrayList(), offset = offset)
    }
}