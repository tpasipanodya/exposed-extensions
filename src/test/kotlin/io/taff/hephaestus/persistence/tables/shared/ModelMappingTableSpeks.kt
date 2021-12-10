package io.taff.hephaestus.persistence.tables.shared

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.traits.ModelMappingTableTrait
import io.taff.hephaestustest.expectation.any.equal
import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.iterable.beAnUnOrderedCollectionOf
import io.taff.hephaestustest.expectation.should
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe


fun<ID, M, T> Root.includeModelMappingTableSpeks(
    table: T,
    recordFunc: () -> M,
    titleColumnRef: Column<String>
) where ID: Comparable<ID>,
        M : Model<ID>,
        M : TitleAware,
        T : IdTable<ID>,
        T : ModelMappingTableTrait<ID, M, T> = describe("model mapping table speks") {

    val record by memoized { recordFunc() }
    val persisted by memoized { transaction { table.insert(record) } }
    val reloaded by memoized { transaction { table.selectAll().map(table::toModel) } }

    describe("insert & select") {
        it("persists the record") {
            persisted should satisfy { size == 1 }
            persisted.toList() should beAnUnOrderedCollectionOf(
                satisfy<M> {
                    isPersisted() &&
                    title == record.title &&
                    createdAt.isNull() &&
                    updatedAt.isNull()

                }
            )
            reloaded should satisfy { size == 1 }
            reloaded should beAnUnOrderedCollectionOf(
                satisfy<M> {
                    isPersisted() &&
                    title == record.title &&
                    !createdAt.isNull() &&
                    !updatedAt.isNull()
                }
            )
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }

        context("via model mapping methods") {
            val updated by memoized {
                transaction {
                    record.title = newTitle
                    table.update(record)
                }
            }

            it("modifies the record") {
                persisted should satisfy { size == 1 }
                persisted.toList() should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                        title == record.title &&
                        createdAt.isNull() &&
                        updatedAt.isNull()

                    }
                )
                updated should equal(true)
                reloaded should satisfy { size == 1 }
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                        title == record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
            }
        }

        context("When the model hasn't been saved yet") {
            val updated by memoized {
                transaction { table.update(record) }
            }

            it("does not modify the record") {
                try { updated; fail("Expected an exception to be raised but none was") }
                catch (e: PersistenceError.UnpersistedUpdateError) {
                    e.message!! should satisfy {
                        contains("Cannot update") &&
                        contains(" because it hasn't been persisted yet")
                    }
                }
                reloaded should satisfy { isEmpty() }
            }
        }

        context("via sql DSL") {
            val updated by memoized {
                transaction {
                    table.update({table.id eq record.id }){
                        it[titleColumnRef] = newTitle
                    }
                }
            }

            it("modifies the record") {
                persisted should satisfy { size == 1 }
                persisted.toList() should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                        title == record.title &&
                        createdAt.isNull() &&
                        updatedAt.isNull()

                    }
                )
                updated should equal(1)
                reloaded should satisfy { size == 1 }
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                        title == newTitle &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
            }
        }
    }


    describe("delete") {
        context("via model mapping methods") {
            val deleted by memoized { transaction { table.delete(*persisted) } }

            it("hard deletes the record") {
                persisted should satisfy { size == 1 }
                persisted.toList() should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                        title == record.title &&
                        createdAt.isNull() &&
                        updatedAt.isNull()

                    }
                )
                deleted should equal(true)
                reloaded should satisfy { isEmpty() }
            }
        }

        context("via sql DSL") {
            val deleted by memoized {
                transaction {
                    table.deleteWhere { table.id eq persisted.first().id }
                }
            }

            it("hard deletes the record") {
                persisted should satisfy { size == 1 }
                persisted.toList() should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        isPersisted() &&
                                title == record.title &&
                                createdAt.isNull() &&
                                updatedAt.isNull()

                    }
                )
                deleted should equal(1)
                reloaded should satisfy { isEmpty() }
            }
        }
    }
}
