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

    val reloaded by memoized {
        transaction {
            table.stripDefaultScope()
                .selectAll()
                .map(table::toModel)
        }
    }

    describe("insert") {
        val record by memoized { recordFxn() }
        val persisted by memoized { transaction { table.insert(record).first() } }

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

    describe("update") {
        val record by memoized { recordFxn() }
        val newTitle by memoized { "groovy soul food" }
        val persisted by memoized { transaction { table.insert(record).first() } }

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
                            table.destroy(persisted)
                            directUpdate(persisted, newTitle, Scope.LIVE)
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
        val persisted by memoized {
            transaction {
                table.insert(recordFxn()).first()
            }
        }

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

//        context("deleting directly") {
//            it("hard deletes the record") {
//                persisted should satisfy { isPersisted() }
//
//                 transaction {
//                    destroyableUuidTable.deleteWhere {
//                        destroyableUuidTable.id eq persisted.id
//                    }
//                }.let { it should equal(1) }
//
//                reloaded should satisfy { isEmpty() }
//            }
//        }
    }

    describe("destroy") {
        val persisted by memoized { transaction { table.insert(recordFxn()).first() } }
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
