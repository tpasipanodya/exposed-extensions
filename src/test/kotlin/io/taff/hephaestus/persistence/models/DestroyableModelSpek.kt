package io.taff.hephaestus.persistence.models

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import com.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*


data class MyDestroyableModel(
    override var id: UUID?,
    override var destroyedAt: OffsetDateTime?,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null
) : DestroyableModel<UUID>

object DestroyableModelSpek : Spek ({

    includeModelSpeks {
        MyDestroyableModel(
            id = it,
            destroyedAt = null
        )
    }

    describe("isDestroyed") {
        val id by memoized { UUID.randomUUID() }

        context("no destroyedAt value set") {
            val model by memoized { MyDestroyableModel(id, null) }

            it("is not destroyed") {
                model shouldNot satisfy { isDestroyed() }
            }
        }

        context("destroyedAt value set") {
            val destroyedAt by memoized { OffsetDateTime.now() }
            val model by memoized { MyDestroyableModel(id, destroyedAt) }

            it("is not destroyed") {
                model should satisfy { isDestroyed() }
            }
        }

    }

    describe("markAsDestroyed") {
        val id by memoized { UUID.randomUUID() }
        val model by memoized { MyDestroyableModel(id, null) }

        it("marks the model as destroyed") {
            model shouldNot satisfy { isDestroyed() }
            model.markAsDestroyed()
            model should satisfy { isDestroyed() }
        }
    }
})