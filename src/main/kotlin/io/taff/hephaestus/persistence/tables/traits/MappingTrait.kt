package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.models.Model
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder

interface MappingTrait<M : Model> {

    fun initializeModel(row: ResultRow) : M

    fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M)
}