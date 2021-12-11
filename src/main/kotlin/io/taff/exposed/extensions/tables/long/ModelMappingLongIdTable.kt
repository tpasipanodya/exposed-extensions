package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.models.Model
import io.taff.exposed.extensions.tables.traits.ModelMappingTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant.now

/**
 * A table that provides basic model mapping functionality.
 * @param M The concrete model type.
 */
abstract class ModelMappingLongIdTable<M : Model<Long>>(name: String)
    :LongIdTable(name), ModelMappingTableTrait<Long, M, ModelMappingLongIdTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }

    override fun self() = this
}

