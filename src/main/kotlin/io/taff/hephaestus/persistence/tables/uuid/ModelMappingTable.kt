package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.traits.MappingTrait
import io.taff.hephaestus.persistence.tables.traits.ModelMappingTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.OffsetDateTime.now

/**
 * A table that provides basic model mapping functionality.
 */
abstract class ModelMappingTable<M : Model>(val name: String) : UUIDTable(name), ModelMappingTableTrait<M, ModelMappingTable<M>> {

    override val createdAt = moment("created_at").clientDefault { now() }
    override val updatedAt = moment("updated_at").clientDefault { now() }

    override fun self() = this
}

