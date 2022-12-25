package io.taff.exposed.extensions.tables.shared

import io.taff.exposed.extensions.TenantError
import io.taff.exposed.extensions.clearCurrentTenantId
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.records.SoftDeletableRecord
import io.taff.exposed.extensions.records.TenantScopedRecord
import io.taff.exposed.extensions.setCurrentTenantId
import io.taff.exposed.extensions.tables.traits.TenantScopedSoftDeletableTableTrait
import io.taff.spek.expekt.any.equal
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.boolean.beTrue
import io.taff.spek.expekt.iterable.containInAnyOrder
import io.taff.spek.expekt.should
import io.taff.spek.expekt.shouldNot
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe
import java.util.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import org.spekframework.spek2.dsl.TestBody


fun <ID : Comparable<ID>, TID : Comparable<TID>, M, T> Root.includeTenantScopedSoftDeletableTableSpeks(
    table: T,
    tenantIdFunc: () -> TID,
    otherTenantIdFunc: () -> TID,
    tenant1RecordsFunc: () -> Array<M>,
    tenant2RecordsFunc: () -> Array<M>,
    titleColumnRef: Column<String>) where T : IdTable<ID>,
                                          T : TenantScopedSoftDeletableTableTrait<ID, TID, M, T>,
                                          M : TenantScopedRecord<ID, TID>,
                                          M : SoftDeletableRecord<ID>,
                                          M : TitleAware = describe("tenant scoped soft deletable table speks") {

    val tenantId by memoized { tenantIdFunc() }
    val otherTenantId by memoized { otherTenantIdFunc() }
    val tenant1records by memoized { tenant1RecordsFunc() }
    val tenant2Records by memoized { tenant2RecordsFunc() }
    val tenant1Record1 by memoized { tenant1records[0] }
    val tenant1Record2 by memoized { tenant1records[1] }
    val tenant2Record1 by memoized { tenant2Records[0] }
    val tenant2Record2 by memoized { tenant2Records[1] }
    val persisted by memoized {
        transaction {
            setCurrentTenantId(tenantId)
            val persistedTenant1Records = table.insert(tenant1Record1, tenant1Record2)

            setCurrentTenantId(otherTenantId)
            val persistedTenant2Records = table.insert(tenant2Record1, tenant2Record2)
            listOf(*persistedTenant1Records, *persistedTenant2Records)
        }
    }
    val reloaded by memoized {
        transaction {
            table.stripDefaultFilter()
                .selectAll()
                .orderBy(table.createdAt, SortOrder.ASC)
                .map(table::toRecord)
        }
    }

    describe("select") {
        val softDeleteRecords by memoized {
            setCurrentTenantId(otherTenantId)
            table.softDelete(tenant2Record2)
            setCurrentTenantId(tenantId)
            table.softDelete(tenant1Record2)
        }
        context("when scope = live") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    setCurrentTenantId(tenantId)
                    table.selectAll().map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 1 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = softDeleted") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    setCurrentTenantId(tenantId)
                    table.softDeleted().selectAll().map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 1 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveAndSoftDeleted") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    table.liveAndSoftDeleted()
                        .selectAll()
                        .map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    },
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveForAllTenants") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    table.liveForAllTenants()
                        .selectAll()
                        .map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    },
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = softDeletedForAllTenants") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    table.softDeletedForAllTenants()
                        .selectAll()
                        .map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    },
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveAndSoftDeletedForAllTenants") {
            val selected by memoized {
                transaction {
                    softDeleteRecords
                    table.liveAndSoftDeletedForAllTenants()
                        .selectAll()
                        .map(table::toRecord)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 4 }
                selected should containInAnyOrder (
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }
    }

    describe("insert") {
        context("with tenant id set") {
            it("persists records and correctly sets their attributes") {
                persisted should satisfy {
                    size == 4 &&
                    get(0).run {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title

                    } && get(1).run {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    } && get(2).run {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    } && get(3).run {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                }
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                   }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }
        }

        context("when tenantId not set") {
            val persisted by memoized {
                transaction {
                    clearCurrentTenantId<UUID>()
                    table.insert(tenant1Record1,
                        tenant1Record2,
                        tenant2Record1,
                        tenant2Record2)
                }
            }

            it("doesn't persist") {
                try { persisted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Record ${tenant1Record1.id} can't be persisted because There's no current tenant Id set." } }

                tenant1Record1 shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }

        context("attempting to insert another tenant's records") {
            val persisted by memoized {
                transaction {
                    tenant1Record1.tenantId = tenantId
                    setCurrentTenantId(otherTenantId)
                    table.insert(tenant1Record1)
                }
            }

            it("doesn't persist") {
                try { persisted; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Record ${tenant1Record1.id} can't be persisted because it doesn't belong to the current tenant." } }

                tenant1Record1 shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }

        context("with tenantId correctly set") {
            val correctlyUpdates: TestBody.(() -> Boolean) -> Unit = { updater ->
                persisted should satisfy {
                    size == 4 && first().title == tenant1Record1.title
                }
                updater() should beTrue()
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == newTitle
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Record<*>::isPersisted) &&
                        !isSoftDeleted() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                )
            }

            context("when scope = live") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.update({ table.id eq tenant1Record1.id }){
                                it[titleColumnRef] = newTitle
                            } == 1
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }
            }

            context("when scope = softDeleted") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.softDeleted().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(false)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.softDeleted()
                                .update({ table.id eq tenant1Record1.id }){
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }
            }

            context("when scope = liveAndSoftDeleted") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.liveAndSoftDeleted().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.liveAndSoftDeleted()
                                .update({ table.id eq tenant1Record1.id }){
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when scope = liveForAllTenants") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.liveForAllTenants().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.liveForAllTenants()
                                .update({ table.id eq tenant1Record1.id }) {
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when all scopes have been striped") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.liveAndSoftDeletedForAllTenants()
                                .update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.liveAndSoftDeletedForAllTenants()
                                .update({ table.id eq tenant1Record1.id }){
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when scope = softDeletedForAllTenants") {
                context("updating via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.softDeletedForAllTenants().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(false)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }

                context("updating directly via the sql dsl") {
                    val updated by memoized {
                        transaction {
                            persisted
                            setCurrentTenantId(tenantId)
                            table.softDeletedForAllTenants()
                                .update({ table.id eq tenant1Record1.id }) {
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant1Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record1.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Record<*>::isPersisted) &&
                                this.let(Record<*>::id) == tenant2Record2.id &&
                                !isSoftDeleted() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }
            }
        }

        context("attempting to update another tenant's records") {
            context("updating via record mapping methods") {
                val updated by memoized {
                    setCurrentTenantId(otherTenantId)
                    tenant1Record1.title = newTitle
                    transaction { table.update(tenant1Record1) }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }

                    try {
                        updated; fail("Expected a tenant error but non was raised.")
                    } catch (e: TenantError) {
                        e.message should satisfy { this == "Record ${tenant1Record1.id} can't be persisted because it doesn't belong to the current tenant." }
                    }

                    reloaded should containInAnyOrder(
                        satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record2.title
                        }
                    )
                }
            }


            context("updating directly via the sql DSL") {
                val updated by memoized {
                    transaction {
                        setCurrentTenantId(otherTenantId)
                        table.update({ table.id eq tenant1Record1.id }){
                            it[titleColumnRef] = newTitle
                        }
                    }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }
                    updated should equal(0)
                    reloaded should containInAnyOrder(
                        satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record2.title
                        }
                    )
                }
            }
        }

        context("No tenant id set") {
            context("updating via record mapping methods") {
                val updated by memoized {
                    clearCurrentTenantId<UUID>()
                    transaction {
                        tenant1Record1.title = newTitle
                        table.update(tenant1Record1)
                    }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }

                    try { updated; fail("Expected an error but non was raised.") }
                    catch (e: Exception) { e.message should satisfy { this == "Record ${tenant1Record1.id} can't be persisted because There's no current tenant Id set." } }

                    reloaded should containInAnyOrder(
                        satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record2.title
                        }
                    )
                }
            }

            context("updating via the sql DSL") {
                val updated by memoized {
                    clearCurrentTenantId<UUID>()
                    transaction {
                        table.update({ table.id eq tenant1Record1.id }){
                            it[titleColumnRef] = newTitle
                        }
                    }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }
                    updated should equal(0)
                    reloaded should containInAnyOrder(
                        satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                                    title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant1Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record1.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Record<*>::isPersisted) &&
                            this.let(Record<*>::id) == tenant2Record2.id &&
                            !isSoftDeleted() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record2.title
                        }
                    )
                }
            }
        }
    }

    describe("delete") {
        context("when scope = live") {
            context("with tenant id correctly set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.deleteWhere { Op.build { table.id eq tenant1Record1.id  } }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }

                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.deleteWhere { Op.build { table.id eq tenant1Record1.id } } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }

                        deleted should equal(0)

                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = softDeleted") {
            context("with tenant id correctly set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.softDeleted().delete(tenant1Record1)
                        }
                    }

                    it("doesn't delete the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { softDeletedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.softDeleted().deleteWhere {  Op.build { table.id eq tenant1Record1.id } }
                        }
                    }

                    it("doesn't delete the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.softDeleted().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.softDeleted().deleteWhere { Op.build { table.id eq tenant1Record1.id } } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = softDeletedForAllTenants") {
            context("with tenant id correctly set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.softDeletedForAllTenants().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { softDeletedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.softDeletedForAllTenants().deleteWhere { Op.build { table.id eq tenant1Record1.id } }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { softDeletedAt.isNull() }
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.softDeletedForAllTenants().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.softDeletedForAllTenants().deleteWhere { Op.build { table.id eq tenant1Record1.id } } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = liveAndSoftDeleted") {
            context("with tenant id correctly set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveAndSoftDeleted().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                    }
                }

                context("via sql dsl") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveAndSoftDeleted().deleteWhere {  Op.build { table.id eq tenant1Record1.id } }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndSoftDeleted().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }

                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }

            context("via sql dsl") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndSoftDeleted().deleteWhere { Op.build { table.id eq tenant1Record1.id } } }
                }

                it("doesn't delete the record because of tenant isolation") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(0)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveForAllTenants") {
            context("with tenant id correctly set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveForAllTenants().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                    }
                }

                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveForAllTenants().deleteWhere { Op.build { table.id eq tenant1Record1.id } }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndSoftDeletedForAllTenants().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }

                context("via the sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndSoftDeletedForAllTenants().deleteWhere {  Op.build { table.id eq tenant1Record1.id } } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Record<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                        )
                    }
                }
            }
        }
    }

    describe("softDelete") {
        context("when scope = live") {
            context("with tenant id correctly set") {
                val softDeleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.softDelete(tenant1Record1)
                    }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    softDeleted should equal(true)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && !softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull()  },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull()  },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull()  }
                    )
                    tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val softDeleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.softDelete(tenant1Record1) }
                }

                it("doesn't soft delete the record because of tenant isolation") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }

                    try { softDeleted; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = softDeleted") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.softDeleted().softDelete(tenant1Record1)
                    }
                }

                it("does not soft delete") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull()},
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { softDeletedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.softDeleted().softDelete(tenant1Record1) }
                }

                it("doesn't soft delete the record because of tenant isolation") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }

                    try { deleted; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = softDeletedForAllTenants") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.softDeletedForAllTenants().softDelete(tenant1Record1)
                    }
                }

                it("hard soft deletes the record") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { softDeletedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.softDeletedForAllTenants().softDelete(tenant1Record1) }
                }

                it("doesn't soft delete the record because of tenant isolation") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveAndSoftDeleted") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.liveAndSoftDeleted().softDelete(tenant1Record1)
                    }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && !softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                }
            }

            context("attempting to delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndSoftDeleted().softDelete(tenant1Record1) }
                }

                it("doesn't delete the record because of tenant isolation") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }

                    try { deleted; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveForAllTenants") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.liveForAllTenants().softDelete(tenant1Record1)
                    }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && !softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull()},
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { !softDeletedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndSoftDeletedForAllTenants().softDelete(tenant1Record1) }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Record<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should containInAnyOrder(
                        satisfy<M> { title == tenant1Record1.title && !softDeletedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && softDeletedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && softDeletedAt.isNull() }
                    )
                }
            }
        }
    }
}
