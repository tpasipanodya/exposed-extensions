package io.taff.hephaestus.persistence.models

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe
import java.util.*

fun <ID : Comparable<UUID>, M : Model<ID>> Root.includeModelSpeks(modelFxn: (id: UUID?) -> M) {
    describe("isPersisted") {
        context("with an id set") {
            val id by memoized { UUID.randomUUID() }
            val model by memoized { modelFxn(id) }

            it("is persisted") {
                model should satisfy { isPersisted() }
            }
        }

        context("with no id set") {
            val id by memoized { null }
            val model by memoized { modelFxn(id) }

            it("is persisted") {
                model shouldNot satisfy { isPersisted() }
            }
        }
    }
}
