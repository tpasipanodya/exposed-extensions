package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.traits.ModelMappingTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now
import java.util.*

/**
 * A table that provides basic model mapping functionality.
 * @param M The concrete model type.
 */
abstract class ModelMappingUuidTable<M : Model<UUID>>(name: String)
    :UUIDTable(name), ModelMappingTableTrait<UUID, M, ModelMappingUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}

