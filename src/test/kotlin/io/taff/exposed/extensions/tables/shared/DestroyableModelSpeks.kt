package io.taff.exposed.extensions.tables.shared

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.SoftDeletableModel
import io.taff.hephaestus.persistence.tables.traits.SoftDeletableTableTrait
import io.taff.hephaestustest.expectation.any.equal
import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.boolean.beFalse
import io.taff.hephaestustest.expectation.boolean.beTrue
import io.taff.hephaestustest.expectation.iterable.beAnUnOrderedCollectionOf
import io.taff.hephaestustest.expectation.should
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe


fun <ID : Comparable<ID>, M, T> Root.includeSoftDeletableTableSpeks(
    table: T,
    recordFxn: () -> M,
    titleColumnRef: Column<String>
) where T : SoftDeletableTableTrait<ID, M, T>,
        T : IdTable<ID>,
        M : SoftDeletableModel<ID>,
        M : TitleAware = describe("soft deletable table speks") {

    val record by memoized { recordFxn() }
    val persisted by memoized {
        transaction { table.insert(record).first() }
    }
    val reloaded by memoized {
        transaction {
            table.stripDefaultScope()
                .selectAll()
                .map(table::toModel)
        }
    }

    describe("insert") {
        it("persists the record") {
            persisted should satisfy {
                this.title == record.title &&
                isPersisted()
            }
            record.isPersisted() should beTrue()
            reloaded should satisfy {
                size == 1 &&
                first().let {
                    it.title == record.title &&
                    !it.createdAt.isNull() &&
                    !it.updatedAt.isNull()
                }
            }
        }
    }

    describe("select") {
        val otherPersisted by memoized { table.insert(recordFxn()).first() }

        context("default scope") {
            val selected by memoized {
                transaction {
                    otherPersisted
                    table.softDelete(persisted)
                    table.selectAll().map(table::toModel)
                }
            }

            it("doesn't load the record") {
                selected should satisfy {
                    size == 1 &&
                    first().run {
                        id == otherPersisted.id &&
                        title == otherPersisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        softDeletedAt.isNull()
                    }
                }
            }
        }

        context("softDeleted scope") {
            val selected by memoized {
                transaction {
                    otherPersisted
                    table.softDelete(persisted)
                    table.softDeleted().selectAll()
                        .map(table::toModel)
                }
            }

            it("doesn't load the record") {
                selected should satisfy {
                    size == 1 &&
                    first().run {
                        title == persisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        !softDeletedAt.isNull()
                    }
                }
            }
        }

        context("liveAndSoftDeleted scope") {
            val selected by memoized {
                transaction {
                    otherPersisted
                    table.softDelete(persisted)
                    table.liveAndSoftDeleted().selectAll()
                        .orderBy(table.id, SortOrder.ASC)
                        .map(table::toModel)
                }
            }

            it("loads all of the current tenant's records") {
                selected should satisfy { size == 2 }
                selected should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        id == otherPersisted.id &&
                        title == otherPersisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        softDeletedAt.isNull()
                    },
                    satisfy<M> {
                        title == persisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        !softDeletedAt.isNull()
                    })
            }
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }

        context("when already persisted") {
            val updated by memoized {
                transaction {
                    persisted.title = newTitle
                    table.update(persisted)
                }
            }

            it("modifies the record") {
                updated should beTrue()
                reloaded should satisfy {
                    size == 1 &&
                    first().let {
                        it.title == newTitle &&
                        !it.createdAt.isNull() &&
                        !it.updatedAt.isNull()
                    }
                }
            }
        }

        context("when updating a soft deleted record") {
            context("when using the default scope") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.softDelete(persisted)
                            persisted.title = newTitle
                            table.update(persisted)
                        }
                    }

                    it("does not modify the record") {
                        updated should beFalse()
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title != newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull()
                            }
                        }
                    }
                }

                context("without model mapping") {
                    val updated by memoized {
                        transaction {
                            persisted
                            table.softDelete(persisted)
                            table.update({ table.id eq persisted.id }){
                                it[titleColumnRef] = newTitle
                            }
                        }
                    }

                    it("does not modify the record") {
                        updated should equal(0)
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title != newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull()
                            }
                        }
                    }
                }
            }

            context("When using the soft deleted scope") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.softDelete(persisted)
                            persisted.title = newTitle
                            table.softDeleted().update(persisted)
                        }
                    }

                    it("modifies the record") {
                        updated should beTrue()
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title == newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull() &&
                                !it.softDeletedAt.isNull()
                            }
                        }
                    }
                }

                context("without model mapping") {
                    val updated by memoized {
                        transaction {
                            table.softDelete(persisted)
                            table.softDeleted()
                                .update({ table.id eq persisted.id }){
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("does not modify the record") {
                        updated should equal(1)
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title == newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull()
                            }
                        }
                    }
                }
            }

            context("When using both live and soft deleted scopes") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.softDelete(persisted)
                            persisted.title = newTitle
                            table.liveAndSoftDeleted().update(persisted)
                        }
                    }

                    it("modifies the record") {
                        updated should beTrue()
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title == newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull() &&
                                !it.softDeletedAt.isNull()
                            }
                        }
                    }
                }

                context("without model maping"){
                    val updated by memoized {
                        transaction {
                            table.softDelete(persisted)
                            table.liveAndSoftDeleted()
                                .update({ table.id eq persisted.id }){
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("does not modify the record") {
                        updated should equal(1)
                        reloaded should satisfy {
                            size == 1 &&
                            first().let {
                                it.title == newTitle &&
                                !it.createdAt.isNull() &&
                                !it.updatedAt.isNull()
                            }
                        }
                    }
                }
            }

        }

        context("When the model hasn't been persisted yet") {
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
            }
        }
    }

    describe("delete") {
        context("with model mapping") {
            val deleted by memoized { transaction { table.delete(persisted) } }

            it("hard deletes the record") {
                persisted should satisfy { isPersisted() }
                deleted should equal(true)
                reloaded should satisfy { isEmpty() }
            }
        }


        context("without model mapping") {
            val deleted by memoized { transaction { table.deleteWhere { table.id eq persisted.id } } }

            it("hard deletes the record") {
                persisted should satisfy { isPersisted() }
                deleted should equal(1)
                reloaded should satisfy { isEmpty() }
            }
        }
    }

    describe("softDelete") {
        val softDeleted by memoized { transaction { table.softDelete(persisted) } }

        it("soft deletes the record") {
            persisted should satisfy { isPersisted() }
            softDeleted should beTrue()
            reloaded should satisfy {
                size == 1 &&
                first().title == persisted.title &&
                !first().softDeletedAt.isNull() }
        }
    }
}
