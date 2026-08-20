package com.example.data.semantic

import com.example.data.db.SemanticRepresentationDao
import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Domain-to-Entity mapper and repository abstraction for versioned multi-vector persistence.
 *
 * Enforces vector mathematical validation, serialization, and compatibility guarantees before
 * writing to encrypted persistence.
 */
interface SemanticRepresentationRepository {
    suspend fun saveRepresentation(representation: SemanticRepresentation)
    suspend fun saveRepresentations(representations: List<SemanticRepresentation>)
    suspend fun getById(id: String): SemanticRepresentation?
    suspend fun getForMedia(mediaId: String): List<SemanticRepresentation>
    fun observeForMedia(mediaId: String): Flow<List<SemanticRepresentation>>
    suspend fun getCompatibleRepresentations(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): List<SemanticRepresentation>
    fun observeCompatibleRepresentations(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): Flow<List<SemanticRepresentation>>
    suspend fun getSpecificRepresentation(
        mediaId: String,
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): SemanticRepresentation?
    suspend fun getByModel(modelId: String, version: Int): List<SemanticRepresentation>
    suspend fun getByType(type: SemanticRepresentationType): List<SemanticRepresentation>
    suspend fun count(): Int
    suspend fun countCompatible(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Int
    suspend fun exists(mediaId: String, type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Boolean
    suspend fun deleteById(id: String)
    suspend fun deleteForMedia(mediaId: String)
    suspend fun deleteByModel(modelId: String, version: Int)
    suspend fun clearAll()
}

/**
 * Production implementation of [SemanticRepresentationRepository] backed by SQLCipher Room DAO.
 */
class RoomSemanticRepresentationRepository(
    private val dao: SemanticRepresentationDao
) : SemanticRepresentationRepository {

    override suspend fun saveRepresentation(representation: SemanticRepresentation) = withContext(Dispatchers.IO) {
        val entity = toEntity(representation)
        dao.upsert(entity)
    }

    override suspend fun saveRepresentations(representations: List<SemanticRepresentation>) = withContext(Dispatchers.IO) {
        val entities = representations.map { toEntity(it) }
        dao.upsertAll(entities)
    }

    override suspend fun getById(id: String): SemanticRepresentation? = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { toDomain(it) }
    }

    override suspend fun getForMedia(mediaId: String): List<SemanticRepresentation> = withContext(Dispatchers.IO) {
        dao.getForMedia(mediaId).mapNotNull { toDomainSafe(it) }
    }

    override fun observeForMedia(mediaId: String): Flow<List<SemanticRepresentation>> {
        return dao.observeForMedia(mediaId).map { entities ->
            entities.mapNotNull { toDomainSafe(it) }
        }
    }

    override suspend fun getCompatibleRepresentations(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): List<SemanticRepresentation> = withContext(Dispatchers.IO) {
        dao.getCompatibleRepresentations(
            type = type.name,
            modelId = descriptor.modelId,
            version = descriptor.modelVersion
        ).mapNotNull { entity ->
            val domain = toDomainSafe(entity)
            if (domain != null && domain.modelDescriptor.isCompatibleWith(descriptor)) {
                domain
            } else {
                null
            }
        }
    }

    override fun observeCompatibleRepresentations(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): Flow<List<SemanticRepresentation>> {
        return dao.observeCompatibleRepresentations(
            type = type.name,
            modelId = descriptor.modelId,
            version = descriptor.modelVersion
        ).map { entities ->
            entities.mapNotNull { entity ->
                val domain = toDomainSafe(entity)
                if (domain != null && domain.modelDescriptor.isCompatibleWith(descriptor)) {
                    domain
                } else {
                    null
                }
            }
        }
    }

    override suspend fun getSpecificRepresentation(
        mediaId: String,
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): SemanticRepresentation? = withContext(Dispatchers.IO) {
        val entity = dao.getSpecificRepresentation(
            mediaId = mediaId,
            type = type.name,
            modelId = descriptor.modelId,
            version = descriptor.modelVersion
        ) ?: return@withContext null

        val domain = toDomainSafe(entity)
        if (domain != null && domain.modelDescriptor.isCompatibleWith(descriptor)) {
            domain
        } else {
            null
        }
    }

    override suspend fun getByModel(modelId: String, version: Int): List<SemanticRepresentation> = withContext(Dispatchers.IO) {
        dao.getByModel(modelId, version).mapNotNull { toDomainSafe(it) }
    }

    override suspend fun getByType(type: SemanticRepresentationType): List<SemanticRepresentation> = withContext(Dispatchers.IO) {
        dao.getByType(type.name).mapNotNull { toDomainSafe(it) }
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    override suspend fun countCompatible(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): Int = withContext(Dispatchers.IO) {
        dao.countCompatible(type.name, descriptor.modelId, descriptor.modelVersion)
    }

    override suspend fun exists(
        mediaId: String,
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ): Boolean = withContext(Dispatchers.IO) {
        dao.exists(mediaId, type.name, descriptor.modelId, descriptor.modelVersion)
    }

    override suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    override suspend fun deleteForMedia(mediaId: String) = withContext(Dispatchers.IO) {
        dao.deleteForMedia(mediaId)
    }

    override suspend fun deleteByModel(modelId: String, version: Int) = withContext(Dispatchers.IO) {
        dao.deleteByModel(modelId, version)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    companion object {
        fun toEntity(domain: SemanticRepresentation): SemanticRepresentationEntity {
            val isNormalized = kotlin.math.abs(VectorMath.magnitude(domain.vector) - 1.0f) < 1e-4f
            val serialized = VectorMath.serialize(domain.vector)

            return SemanticRepresentationEntity(
                id = domain.id,
                mediaId = domain.mediaId,
                representationType = domain.type.name,
                modelId = domain.modelDescriptor.modelId,
                modelVersion = domain.modelDescriptor.modelVersion,
                dimensionality = domain.dimensionality,
                vectorData = serialized,
                isNormalized = isNormalized,
                sourceDataHash = domain.sourceDataHash,
                confidence = domain.confidence,
                createdAt = domain.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        }

        fun toDomain(entity: SemanticRepresentationEntity): SemanticRepresentation {
            val type = SemanticRepresentationType.valueOf(entity.representationType)
            val descriptor = EmbeddingModelDescriptor(
                modelId = entity.modelId,
                modelVersion = entity.modelVersion,
                dimensionality = entity.dimensionality,
                primaryType = type
            )
            val vector = VectorMath.deserialize(entity.vectorData, expectedDimension = entity.dimensionality)

            return SemanticRepresentation(
                id = entity.id,
                mediaId = entity.mediaId,
                type = type,
                modelDescriptor = descriptor,
                dimensionality = entity.dimensionality,
                vector = vector,
                sourceDataHash = entity.sourceDataHash,
                confidence = entity.confidence,
                createdAt = entity.createdAt
            )
        }

        fun toDomainSafe(entity: SemanticRepresentationEntity): SemanticRepresentation? {
            return try {
                toDomain(entity)
            } catch (_: Exception) {
                null
            }
        }
    }
}
