package io.taff.exposed.extensions.models

import io.taff.exposed.extensions.models.examples.includeModelSpeks
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import io.taff.spek.expekt.shouldNot
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

    includeModelSpeks(UUID.randomUUID()) {
        MySoftDeletableModel(id = it, softDeletedAt = null)
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
