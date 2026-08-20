package com.example.data.semantic

import com.example.data.db.SemanticRepresentationDao
import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class FakeSemanticRepresentationDao : SemanticRepresentationDao {
    private val db = ConcurrentHashMap<String, SemanticRepresentationEntity>()
    private val _flow = MutableStateFlow<List<SemanticRepresentationEntity>>(emptyList())

    private fun updateFlow() {
        _flow.value = db.values.toList()
    }

    override suspend fun upsert(representation: SemanticRepresentationEntity) {
        db[representation.id] = representation
        updateFlow()
    }

    override suspend fun upsertAll(representations: List<SemanticRepresentationEntity>) {
        for (r in representations) {
            db[r.id] = r
        }
        updateFlow()
    }

    override suspend fun update(representation: SemanticRepresentationEntity) {
        db[representation.id] = representation
        updateFlow()
    }

    override suspend fun getById(id: String): SemanticRepresentationEntity? = db[id]

    override suspend fun getForMedia(mediaId: String): List<SemanticRepresentationEntity> {
        return db.values.filter { it.mediaId == mediaId }
    }

    override fun observeForMedia(mediaId: String): Flow<List<SemanticRepresentationEntity>> {
        return _flow.map { list -> list.filter { it.mediaId == mediaId } }
    }

    override suspend fun getCompatibleRepresentations(
        type: String,
        modelId: String,
        version: Int
    ): List<SemanticRepresentationEntity> {
        return db.values.filter {
            it.representationType == type && it.modelId == modelId && it.modelVersion == version
        }
    }

    override fun observeCompatibleRepresentations(
        type: String,
        modelId: String,
        version: Int
    ): Flow<List<SemanticRepresentationEntity>> {
        return _flow.map { list ->
            list.filter { it.representationType == type && it.modelId == modelId && it.modelVersion == version }
        }
    }

    override suspend fun getSpecificRepresentation(
        mediaId: String,
        type: String,
        modelId: String,
        version: Int
    ): SemanticRepresentationEntity? {
        return db.values.firstOrNull {
            it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version
        }
    }

    override suspend fun getByModel(modelId: String, version: Int): List<SemanticRepresentationEntity> {
        return db.values.filter { it.modelId == modelId && it.modelVersion == version }
    }

    override suspend fun getByType(type: String): List<SemanticRepresentationEntity> {
        return db.values.filter { it.representationType == type }
    }

    override suspend fun count(): Int = db.size

    override suspend fun countCompatible(type: String, modelId: String, version: Int): Int {
        return db.values.count {
            it.representationType == type && it.modelId == modelId && it.modelVersion == version
        }
    }

    override suspend fun exists(mediaId: String, type: String, modelId: String, version: Int): Boolean {
        return db.values.any {
            it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version
        }
    }

    override suspend fun deleteById(id: String) {
        db.remove(id)
        updateFlow()
    }

    override suspend fun deleteForMedia(mediaId: String) {
        val toRemove = db.filterValues { it.mediaId == mediaId }.keys
        for (k in toRemove) {
            db.remove(k)
        }
        updateFlow()
    }

    override suspend fun deleteByModel(modelId: String, version: Int) {
        val toRemove = db.filterValues { it.modelId == modelId && it.modelVersion == version }.keys
        for (k in toRemove) {
            db.remove(k)
        }
        updateFlow()
    }

    override suspend fun clearAll() {
        db.clear()
        updateFlow()
    }
}
