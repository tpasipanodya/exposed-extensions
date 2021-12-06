package io.taff.hephaestus.persistence.tables.shared

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import io.taff.hephaestustest.expectation.any.equal
import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.boolean.beFalse
import io.taff.hephaestustest.expectation.boolean.beTrue
import io.taff.hephaestustest.expectation.should
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe

enum class Scope {
    LIVE,
    DELETED,
    ALL
}

fun <ID : Comparable<ID>, M, T> Root.includeDestroyableModelSpeks(table: T,
                                                                  recordFxn: () -> M,
                                                                  directUpdate: (model: M, newTitle: String, scope: Scope) -> Int)
where T : DestroyableTableTrait<ID, M, T>,
      T : IdTable<ID>,
      M : DestroyableModel<ID>,
      M : TitleAware = describe("destroyable model speks") {

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
                    table.destroy(persisted)
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
                        destroyedAt.isNull()
                    }
                }
            }
        }

        context("destroyed scope") {
            val selected by memoized {
                transaction {
                    otherPersisted
                    table.destroy(persisted)
                    table.destroyed().selectAll()
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
                        !destroyedAt.isNull()
                    }
                }
            }
        }

        context("includingDestroyed scope") {
            val selected by memoized {
                transaction {
                    otherPersisted
                    table.destroy(persisted)
                    table.includingDestroyed().selectAll()
                        .orderBy(table.id, SortOrder.ASC)
                        .map(table::toModel)
                }
            }

            it("doesn't load the record") {
                selected should satisfy {
                    size == 2 &&
                    get(0).run {
                        id == otherPersisted.id &&
                        title == otherPersisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        destroyedAt.isNull()
                    } && get(1).run {
                        title == persisted.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull() &&
                        !destroyedAt.isNull()
                    }
                }
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

        context("when updating a destroyed record") {
            context("when using the default scope") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.destroy(persisted)
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
                             table.destroy(persisted)
                            directUpdate(persisted, newTitle, Scope.LIVE)
                        }gi
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

            context("When using the destroyed scope") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.destroy(persisted)
                            persisted.title = newTitle
                            table.destroyed().update(persisted)
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
                                !it.destroyedAt.isNull()
                            }
                        }
                    }
                }

                context("without model mapping") {
                    val updated by memoized {
                        transaction {
                            table.destroy(persisted)
                            directUpdate(persisted, newTitle, Scope.DELETED)
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

            context("When using both live and destroyed scopes") {
                context("with model mapping") {
                    val updated by memoized {
                        transaction {
                            table.destroy(persisted)
                            persisted.title = newTitle
                            table.includingDestroyed().update(persisted)
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
                                !it.destroyedAt.isNull()
                            }
                        }
                    }
                }

                context("without model maping"){
                    val updated by memoized {
                        transaction {
                            table.destroy(persisted)
                            directUpdate(persisted, newTitle, Scope.ALL)
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
                try {
                    updated
                    fail("Expected an exception to be raised but none was")
                } catch (e: PersistenceError.UnpersistedUpdateError) {
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

    describe("destroy") {
        val destroyed by memoized { transaction { table.destroy(persisted) } }

        it("soft deletes the record") {
            persisted should satisfy { isPersisted() }
            destroyed should beTrue()
            reloaded should satisfy {
                size == 1 &&
                first().title == persisted.title &&
                !first().destroyedAt.isNull() }
        }
    }
}