package io.taff.exposed.extensions.tables.shared

import io.taff.exposed.extensions.TenantError
import io.taff.exposed.extensions.clearCurrentTenantId
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.records.TenantScopedRecord
import io.taff.exposed.extensions.setCurrentTenantId
import io.taff.exposed.extensions.tables.traits.TenantScopedTableTrait
import java.util.*
import io.taff.spek.expekt.any.equal
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.boolean.beTrue
import io.taff.spek.expekt.iterable.containInAnyOrder
import io.taff.spek.expekt.should
import io.taff.spek.expekt.shouldNot
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe


fun <ID : Comparable<ID>, TID : Comparable<TID>, M, T> Root.includeTenantScopedTableSpeks(
    table: T,
    tenantIdFunc: () -> TID,
    tenant2IdFunc: () -> TID,
    tenant1RecordFunc: () -> M,
    tenant2RecordFunc: () -> M,
    titleColumnRef: Column<String>
) where T : IdTable<ID>,
        T : TenantScopedTableTrait<ID, TID, M, T>,
        M : TenantScopedRecord<ID, TID>,
        M : TitleAware = describe("tenant scoped table") {

    beforeEachTest { transaction { table.stripDefaultFilter().deleteAll() } }

    afterEachTest { clearCurrentTenantId() }

    val tenantId by memoized { tenantIdFunc() }
    val otherTenantId by memoized { tenant2IdFunc() }
    val tenant1Record by memoized { tenant1RecordFunc() }
    val tenant2Record by memoized { tenant2RecordFunc() }
    val persisted by memoized {
        transaction {
            setCurrentTenantId(tenantId)
            val persistedTenant1Records = table.insert(tenant1Record)

            setCurrentTenantId(otherTenantId)
            val persistedTenant2Records = table.insert(tenant2Record)
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

    describe("insert") {
        context("with tenant id set") {
            it("persists") {
                persisted should containInAnyOrder(
                    satisfy<M> {
                        isPersisted() &&
                        title == tenant1Record.title &&
                        this.tenantId == tenantId
                    },
                    satisfy<M> {
                        isPersisted() &&
                        title == tenant2Record.title &&
                        this.tenantId == otherTenantId
                    }
                )
                reloaded should satisfy { size == 2 }
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        isPersisted() &&
                        title == tenant1Record.title &&
                        this.tenantId == tenantId &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    },
                    satisfy<M> {
                        isPersisted() &&
                        title == tenant2Record.title &&
                        this.tenantId == otherTenantId &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
            }
        }

        context("when tenantId not set") {
            val persistedNoTenant by memoized {
                transaction {
                    clearCurrentTenantId<UUID>()
                    table.insert(tenant1Record, tenant2Record)
                }
            }

            it("doesn't persist") {
                try { persistedNoTenant; fail("Expected an error to be raised but non was") }
                catch (e: TenantError) { e should satisfy { message == "Record ${tenant1Record.id} can't be persisted because There's no current tenant Id set." } }
                tenant1Record shouldNot satisfy { isPersisted() }
                tenant2Record shouldNot satisfy { isPersisted() }
                reloaded should satisfy { isEmpty() }
            }
        }
    }

    describe("select") {
        context("scope = forCurrentTenant") {
            val selected by memoized {
                transaction {
                    persisted
                    setCurrentTenantId(tenantId)
                    table.selectAll().map(table::toRecord)
                }
            }

            it("only loads the current tenant's records") {
                selected should satisfy { size == 1 }
                selected should containInAnyOrder(
                    satisfy<M> {
                        id == tenant1Record.id &&
                        title == tenant1Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
                reloaded should satisfy { size == 2 }
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        id == tenant1Record.id &&
                        title == tenant1Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    },
                    satisfy<M> {
                        id == tenant2Record.id &&
                        title == tenant2Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
            }
        }

        context("scope = forAllTenants") {
            val selected by memoized {
                transaction {
                    persisted
                    setCurrentTenantId(tenantId)
                    table.forAllTenants()
                        .selectAll()
                        .map(table::toRecord)
                }
            }

            it("only loads the current tenant's records") {
                selected should satisfy { size == 2 }
                selected should containInAnyOrder(
                    satisfy<M> {
                        id == tenant1Record.id &&
                        title == tenant1Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    },
                    satisfy<M> {
                        id == tenant2Record.id &&
                        title == tenant2Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
                reloaded should satisfy { size == 2 }
                reloaded should containInAnyOrder(
                    satisfy<M> {
                        id == tenant1Record.id &&
                        title == tenant1Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    },
                    satisfy<M> {
                        id == tenant2Record.id &&
                        title == tenant2Record.title &&
                        !createdAt.isNull() &&
                        !updatedAt.isNull()
                    }
                )
            }
        }
    }

    describe("update") {
        val newTitle by memoized { "groovy soul food" }

        context("scope = forCurrentTenant") {
            context("with tenantId correctly set") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record.title = newTitle
                            table.update(tenant1Record)
                        }
                    }

                    it("updates") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should beTrue()
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                        reloaded should satisfy { size == 2 }
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.update({ table.id eq tenant1Record.id }) {
                                it[titleColumnRef!!] = newTitle
                            }
                        }
                    }

                    it("updates") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                        reloaded should satisfy { size == 2 }
                    }
                }
            }

            context("attempting to update another tenant's records") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.update(tenant2Record)
                        }
                    }

                    it("doesn't update because of tenant isolation") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )

                        try { updated; fail("Expected a tenant error but non was raised.") }
                        catch (e: TenantError) { e.message should satisfy { this == "Record ${tenant2Record.id} can't be persisted because it doesn't belong to the current tenant." } }

                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.update({ table.id eq tenant2Record.id }) {
                                it[titleColumnRef!!] = newTitle
                            }
                        }
                    }

                    it("doesn't update because of tenant isolation") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }
            }

            context("No tenant id set") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            tenant1Record.title = newTitle
                            table.update(tenant1Record)
                        }
                    }

                    it("doesn't update because of tenant isolation") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        try { updated; fail("Expected an error but non was raised.") }
                        catch (e: Exception) { e.message should satisfy { this == "Record ${tenant1Record.id} can't be persisted because There's no current tenant Id set." } }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1RecordFunc().title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.update({ table.id eq tenant1Record.id }) {
                                it[titleColumnRef!!] = newTitle
                            }
                        }
                    }

                    it("doesn't update because of tenant isolation") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(0)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }
            }
        }

        context("scope = forAllTenants") {
            context("with tenantId correctly set") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record.title = newTitle
                            table.forAllTenants().update(tenant1Record)
                        }
                    }

                    it("updates") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should beTrue()
                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.forAllTenants()
                                .update({ table.id eq tenant1Record.id }) {
                                    it[titleColumnRef!!] = newTitle
                                }
                        }
                    }

                    it("updates") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(1)
                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }
            }

            context("attempting to update another tenant's records") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant2Record.title = newTitle
                            table.forAllTenants().update(tenant2Record)
                        }
                    }

                    it("updates the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == newTitle &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.forAllTenants()
                                .update({ table.id eq tenant2Record.id}) {
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == newTitle &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }
            }

            context("No tenant id set") {
                context("via record mapping methods") {
                    val updated by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            tenant1Record.title = newTitle
                            table.forAllTenants().update(tenant1Record)
                        }
                    }

                    it("updates the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(true)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val updated by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.forAllTenants()
                                .update({ table.id eq tenant1Record.id}) {
                                    it[titleColumnRef] = newTitle
                                }
                        }
                    }

                    it("updates the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        updated should equal(1)
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                title == newTitle &&
                                isPersisted() &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }, satisfy<M> {
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull() &&
                                isPersisted()
                            }
                        )
                    }
                }
            }
        }
    }

    describe("delete") {
        context("scope = forCurrentTenant") {
            context("tenant Id set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(tenantId)
                        transaction { table.delete(tenant1Record) }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(true)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                isPersisted() &&
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(tenantId)
                        transaction { table.deleteWhere { table.id eq tenant1Record.id } }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(1)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.delete(tenant2Record)
                        }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        try { deleted; fail("Expected an error to be raised but non was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because they belong to a different tenant." } }

                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                isPersisted() &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            },
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.deleteWhere { table.id eq tenant2Record.id }
                        }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(0)
                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                isPersisted() &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            },
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }
            }

            context("With no tenant id set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.delete(tenant1Record)
                        }
                    }

                    it("doesn't delete the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        try { deleted; fail("Expected an error to be raised but non was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot delete records because there is no CurrentTenantId." } }

                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                isPersisted() &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            },
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.deleteWhere { table.id eq tenant1Record.id }
                        }
                    }

                    it("doesn't delete the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(0)
                        reloaded should satisfy { size == 2 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                isPersisted() &&
                                id == tenant1Record.id &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            },
                            satisfy<M> {
                                isPersisted() &&
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                    }
                        )
                    }
                }
            }
        }

        context("scope = forAllTenants") {
            context("tenant Id set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(tenantId)
                        transaction { table.forAllTenants().delete(tenant1Record) }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(true)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                isPersisted() &&
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.forAllTenants()
                                .deleteWhere { table.id eq tenant1Record.id }
                        }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(1)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                isPersisted() &&
                                id == tenant2Record.id &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.forAllTenants()
                                .delete(tenant2Record)
                        }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(true)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                isPersisted() &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.forAllTenants()
                                .deleteWhere { table.id eq tenant2Record.id }
                        }
                    }

                    it("deletes the records") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(1)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant1Record.id &&
                                isPersisted() &&
                                title == tenant1Record.title &&
                                this.tenantId == tenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }
            }

            context("With no tenant id set") {
                context("via record mapping methods") {
                    val deleted by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.forAllTenants()
                                .delete(tenant1Record)
                        }
                    }

                    it("deletes the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(true)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            clearCurrentTenantId<UUID>()
                            table.forAllTenants()
                                .deleteWhere { table.id eq tenant1Record.id }
                        }
                    }

                    it("deletes the record") {
                        persisted should containInAnyOrder(
                            satisfy<M> { title == tenant1Record.title && isPersisted() },
                            satisfy<M> { title == tenant2Record.title && isPersisted() }
                        )
                        deleted should equal(1)
                        reloaded should satisfy { size == 1 }
                        reloaded should containInAnyOrder(
                            satisfy<M> {
                                id == tenant2Record.id &&
                                isPersisted() &&
                                title == tenant2Record.title &&
                                this.tenantId == otherTenantId &&
                                !createdAt.isNull() &&
                                !updatedAt.isNull()
                            }
                        )
                    }
                }
            }
        }
    }
}
