package io.taff.hephaestus.persistence.tables.shared

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.clearCurrentTenantId
import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.models.TenantScopedModel
import io.taff.hephaestus.persistence.setCurrentTenantId
import io.taff.hephaestus.persistence.tables.traits.TenantScopedDestroyableTableTrait
import io.taff.hephaestustest.expectation.any.equal
import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.boolean.beTrue
import io.taff.hephaestustest.expectation.iterable.beAnUnOrderedCollectionOf
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.fail
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe
import java.util.*
import org.jetbrains.exposed.sql.deleteWhere
import org.spekframework.spek2.dsl.TestBody

enum class DestroyableTenantScope {
    LIVE_FOR_TENANT,
    DELETED_FOR_TENANT,
    LIVE_AND_DESTROYED_FOR_CURRENT_TENANT,
    LIVE_FOR_ALL_TENANTS,
    DELETED_FOR_ALL_TENANTS,
    LIVE_AND_DESTROYED_FOR_ALL_TENANTS
}

fun <ID : Comparable<ID>, TID : Comparable<TID>, M, T> Root.includeTenantScopedDestroyableTableSpeks(
    table: T,
    tenantIdFunc: () -> TID,
    otherTenantIdFunc: () -> TID,
    tenant1RecordsFunc: () -> Array<M>,
    tenant2RecordsFunc: () -> Array<M>,
    directUpdateFunc: (record: M, newTitle: String, scope: DestroyableTenantScope) -> Int
) where T : IdTable<ID>,
        T : TenantScopedDestroyableTableTrait<ID, TID, M, T>,
        M : TenantScopedModel<ID, TID>,
        M : DestroyableModel<ID>,
        M : TitleAware = describe("tenant scoped destroyable table speks") {

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
            table.stripDefaultScope()
                .selectAll()
                .orderBy(table.createdAt, SortOrder.ASC)
                .map(table::toModel)
        }
    }

    describe("select") {
        val destroyRecords by memoized {
            setCurrentTenantId(otherTenantId)
            table.destroy(tenant2Record2)
            setCurrentTenantId(tenantId)
            table.destroy(tenant1Record2)
        }
        context("when scope = live") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    setCurrentTenantId(tenantId)
                    table.selectAll().map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 1 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = destroyed") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    setCurrentTenantId(tenantId)
                    table.destroyed().selectAll().map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 1 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveAndDestroyed") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    table.liveAndDestroyed()
                        .selectAll()
                        .map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    },
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveForAllTenants") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    table.liveForAllTenants()
                        .selectAll()
                        .map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    },
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = destroyedForAllTenants") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    table.destroyedForAllTenants()
                        .selectAll()
                        .map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 2 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    },
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }
        }

        context("when scope = liveAndDestroyedForAllTenants") {
            val selected by memoized {
                transaction {
                    destroyRecords
                    table.liveAndDestroyedForAllTenants()
                        .selectAll()
                        .map(table::toModel)
                }
            }

            it("correctly loads") {
                persisted should satisfy { size == 4 }
                selected should satisfy { size == 4 }
                selected should beAnUnOrderedCollectionOf (
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                isDestroyed() &&
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
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title

                    } && get(1).run {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    } && get(2).run {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    } && get(3).run {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record2.title
                    }
                }
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record1.title
                   }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == tenantId &&
                        title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
                        this.tenantId == otherTenantId &&
                        title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                        !isDestroyed() &&
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
                catch (e: TenantError) { e should satisfy { message == "Model ${tenant1Record1.id} can't be persisted because There's no current tenant Id set." } }

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
                catch (e: TenantError) { e should satisfy { message == "Model ${tenant1Record1.id} can't be persisted because it doesn't belong to the current tenant." } }

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
                reloaded should beAnUnOrderedCollectionOf(
                    satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == newTitle
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                    }, satisfy<M> {
                        this.let(Model<*>::isPersisted) &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                    }
                )
            }

            context("when scope = live") {
                context("updating via model mapping methods") {
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.LIVE_FOR_TENANT
                            ) == 1
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated }
                    }
                }
            }

            context("when scope = destroyed") {
                context("updating via model mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.destroyed().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(false)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record2.id &&
                                !isDestroyed() &&
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.DELETED_FOR_TENANT
                            )
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }
            }

            context("when scope = liveAndDestroyed") {
                context("updating via model mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.liveAndDestroyed().update(tenant1Record1)
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_CURRENT_TENANT
                            )
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when scope = liveForAllTenants") {
                context("updating via model mapping methods") {
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.LIVE_FOR_ALL_TENANTS
                            )
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when all scopes have been striped") {
                context("updating via model mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.liveAndDestroyedForAllTenants()
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.LIVE_AND_DESTROYED_FOR_ALL_TENANTS
                            )
                        }
                    }

                    it("updates") {
                        correctlyUpdates { updated == 1 }
                    }
                }
            }

            context("when scope = destroyedForAllTenants") {
                context("updating via model mapping methods") {
                    val updated by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            tenant1Record1.title = newTitle
                            table.destroyedForAllTenants().update(tenant1Record1)
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(false)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record2.id &&
                                !isDestroyed() &&
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
                            directUpdateFunc(
                                tenant1Record1,
                                newTitle,
                                DestroyableTenantScope.DELETED_FOR_ALL_TENANTS
                            )
                        }
                    }

                    it("updates") {
                        persisted should satisfy {
                            size == 4 && first().title == tenant1Record1.title
                        }
                        updated should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1RecordsFunc().first().title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant1Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == tenantId &&
                                title == tenant1Record2.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record1.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record1.title
                            }, satisfy<M> {
                                this.let(Model<*>::isPersisted) &&
                                this.let(Model<*>::id) == tenant2Record2.id &&
                                !isDestroyed() &&
                                this.tenantId == otherTenantId &&
                                title == tenant2Record2.title
                            }
                        )
                    }
                }
            }
        }

        context("attempting to update another tenant's records") {
            context("updating via model mapping methods") {
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
                        e.message should satisfy { this == "Model ${tenant1Record1.id} can't be persisted because it doesn't belong to the current tenant." }
                    }

                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record2.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record2.id &&
                            !isDestroyed() &&
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
                        directUpdateFunc(
                            tenant1Record1,
                            newTitle,
                            DestroyableTenantScope.LIVE_FOR_TENANT
                        )
                    }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }
                    updated should equal(0)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record2.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record2.id &&
                            !isDestroyed() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record2.title
                        }
                    )
                }
            }
        }

        context("No tenant id set") {
            context("updating via model mapping methods") {
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
                    catch (e: Exception) { e.message should satisfy { this == "Model ${tenant1Record1.id} can't be persisted because There's no current tenant Id set." } }

                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record2.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record2.id &&
                            !isDestroyed() &&
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
                        directUpdateFunc(tenant1Record1, newTitle, DestroyableTenantScope.LIVE_FOR_TENANT)
                    }
                }

                it("doesn't update because of tenant isolation") {
                    persisted should satisfy {
                        size == 4 && first().title == tenant1Record1.title
                    }
                    updated should equal(0)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                                    title == tenant1RecordsFunc().first().title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant1Record2.id &&
                            !isDestroyed() &&
                            this.tenantId == tenantId &&
                            title == tenant1Record2.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record1.id &&
                            !isDestroyed() &&
                            this.tenantId == otherTenantId &&
                            title == tenant2Record1.title
                        }, satisfy<M> {
                            this.let(Model<*>::isPersisted) &&
                            this.let(Model<*>::id) == tenant2Record2.id &&
                            !isDestroyed() &&
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
                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title },
                            satisfy<M> { title == tenant2Record1.title },
                            satisfy<M> { title == tenant2Record2.title }
                        )
                        tenant1Record1 should satisfy { !destroyedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.deleteWhere { table.id eq tenant1Record1.id }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title },
                            satisfy<M> { title == tenant2Record1.title },
                            satisfy<M> { title == tenant2Record2.title }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }

                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.deleteWhere { table.id eq tenant1Record1.id } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }

                        deleted should equal(0)

                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = destroyed") {
            context("with tenant id correctly set") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.destroyed().delete(tenant1Record1)
                        }
                    }

                    it("doesn't delete the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { destroyedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.destroyed().deleteWhere { table.id eq tenant1Record1.id }
                        }
                    }

                    it("doesn't delete the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.destroyed().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.destroyed().deleteWhere { table.id eq tenant1Record1.id } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = destroyedForAllTenants") {
            context("with tenant id correctly set") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.destroyedForAllTenants().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { destroyedAt.isNull() }
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.destroyedForAllTenants().deleteWhere { table.id eq tenant1Record1.id }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { destroyedAt.isNull() }
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.destroyedForAllTenants().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(false)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }

                context("via sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.destroyedForAllTenants().deleteWhere { table.id eq tenant1Record1.id } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(0)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }
        }

        context("when scope = liveAndDestroyed") {
            context("with tenant id correctly set") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveAndDestroyed().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { !destroyedAt.isNull() }
                    }
                }

                context("via sql dsl") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveAndDestroyed().deleteWhere { table.id eq tenant1Record1.id }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndDestroyed().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }

                        try { deleted; fail("Expected an exception to be raised but none was") }
                        catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }

            context("via sql dsl") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndDestroyed().deleteWhere { table.id eq tenant1Record1.id } }
                }

                it("doesn't delete the record because of tenant isolation") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(0)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveForAllTenants") {
            context("with tenant id correctly set") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveForAllTenants().delete(tenant1Record1)
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                        tenant1Record1 should satisfy { !destroyedAt.isNull() }
                    }
                }

                context("via model mapping methods") {
                    val deleted by memoized {
                        transaction {
                            setCurrentTenantId(tenantId)
                            table.liveForAllTenants().deleteWhere { table.id eq tenant1Record1.id }
                        }
                    }

                    it("hard deletes the record") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }

            context("attempting to delete another tenant's records") {
                context("via model mapping methods") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndDestroyedForAllTenants().delete(tenant1Record1) }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(true)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }

                context("via the sql DSL") {
                    val deleted by memoized {
                        setCurrentTenantId(otherTenantId)
                        transaction { table.liveAndDestroyedForAllTenants().deleteWhere { table.id eq tenant1Record1.id } }
                    }

                    it("doesn't delete the record because of tenant isolation") {
                        persisted should satisfy { all(Model<ID>::isPersisted) }
                        deleted should equal(1)
                        reloaded should beAnUnOrderedCollectionOf(
                            satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                            satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                        )
                    }
                }
            }
        }
    }

    describe("destroy") {
        context("when scope = live") {
            context("with tenant id correctly set") {
                val destroyed by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.destroy(tenant1Record1)
                    }
                }

                it("hard deletes the record") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    destroyed should equal(true)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && !destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull()  },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull()  },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull()  }
                    )
                    tenant1Record1 should satisfy { !destroyedAt.isNull() }
                }
            }

            context("attempting to destroy another tenant's records") {
                val destroyed by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.destroy(tenant1Record1) }
                }

                it("doesn't delete the record because of tenant isolation") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }

                    try { destroyed; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = destroyed") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.destroyed().destroy(tenant1Record1)
                    }
                }

                it("does not soft delete") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull()},
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { destroyedAt.isNull() }
                }
            }

            context("attempting to destroy another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.destroyed().destroy(tenant1Record1) }
                }

                it("doesn't soft delete the record because of tenant isolation") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }

                    try { deleted; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = destroyedForAllTenants") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.destroyedForAllTenants().destroy(tenant1Record1)
                    }
                }

                it("hard soft deletes the record") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { destroyedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.destroyedForAllTenants().destroy(tenant1Record1) }
                }

                it("doesn't soft delete the record because of tenant isolation") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(false)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveAndDestroyed") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.liveAndDestroyed().destroy(tenant1Record1)
                    }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && !destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { !destroyedAt.isNull() }
                }
            }

            context("attempting to delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndDestroyed().destroy(tenant1Record1) }
                }

                it("doesn't delete the record because of tenant isolation") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }

                    try { deleted; fail("Expected an exception to be raised but none was") }
                    catch (e: TenantError) { e.message should satisfy { this == "Cannot destroy models because they belong to a different tenant." } }

                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }

        context("when scope = liveForAllTenants") {
            context("with tenant id correctly set") {
                val deleted by memoized {
                    transaction {
                        setCurrentTenantId(tenantId)
                        table.liveForAllTenants().destroy(tenant1Record1)
                    }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && !destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull()},
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                    tenant1Record1 should satisfy { !destroyedAt.isNull() }
                }
            }

            context("attempting to soft delete another tenant's records") {
                val deleted by memoized {
                    setCurrentTenantId(otherTenantId)
                    transaction { table.liveAndDestroyedForAllTenants().destroy(tenant1Record1) }
                }

                it("soft deletes the record") {
                    persisted should satisfy { all(Model<ID>::isPersisted) }
                    deleted should equal(true)
                    reloaded should beAnUnOrderedCollectionOf(
                        satisfy<M> { title == tenant1Record1.title && !destroyedAt.isNull() },
                        satisfy<M> { title == tenant1Record2.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record1.title && destroyedAt.isNull() },
                        satisfy<M> { title == tenant2Record2.title && destroyedAt.isNull() }
                    )
                }
            }
        }
    }
}
