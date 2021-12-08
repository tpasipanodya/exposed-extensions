package io.taff.hephaestus.persistence.models

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.Instant.now
import java.util.*


data class MySoftDeletableModel(
    override var id: UUID?,
    override var softDeletedAt: Instant?,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : SoftDeletableModel<UUID>

object SoftDeletableModelSpek : Spek ({

    includeModelSpeks {
        MySoftDeletableModel(
            id = it,
            softDeletedAt = null
        )
    }

    describe("isSoftDeleted") {
        val id by memoized { UUID.randomUUID() }

        context("no softDeletedAt value set") {
            val model by memoized { MySoftDeletableModel(id, null) }

            it("is not soft deleted") {
                model shouldNot satisfy { isSoftDeleted() }
            }
        }

        context("softDeletedAt value set") {
            val softDeletedAt by memoized { now() }
            val model by memoized { MySoftDeletableModel(id, softDeletedAt) }

            it("is not soft deleted") {
                model should satisfy { isSoftDeleted() }
            }
        }

    }

    describe("markAsSoftDeleted") {
        val id by memoized { UUID.randomUUID() }
        val model by memoized { MySoftDeletableModel(id, null) }

        it("marks the model as soft deleted") {
            model shouldNot satisfy { isSoftDeleted() }
            model.markAsSoftDeleted()
            model should satisfy { isSoftDeleted() }
        }
    }
})
