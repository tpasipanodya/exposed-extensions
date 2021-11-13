package io.taff.hephaestus.persistence.tables.long

import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.postgres.moment
import io.taff.hephaestus.persistence.tables.traits.ModelMappingTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import java.time.OffsetDateTime.now

/**
 * A table that provides basic model mapping functionality.
 * @param M The concrete model type.
 */
abstract class ModelMappingLongIdTable<M : Model<Long>>(val name: String)
    :LongIdTable(name), ModelMappingTableTrait<Long, M, ModelMappingLongIdTable<M>> {

    override val createdAt = moment("created_at").clientDefault { now() }
    override val updatedAt = moment("updated_at").clientDefault { now() }

    override fun self() = this
}

