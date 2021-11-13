package io.taff.hephaestus.persistence.tables.examples

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableModelTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.spekframework.spek2.dsl.Root

fun <M : DestroyableModel, T> Root.includeDestroyableModelTableSpeks(table: T)
    where T : UUIDTable, T : DestroyableModelTableTrait<M, T> {



}