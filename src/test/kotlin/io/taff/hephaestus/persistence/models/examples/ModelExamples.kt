package io.taff.hephaestus.persistence.models

import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe
import java.util.*

fun <ID : Comparable<ID>, M : Model<ID>> Root.includeModelSpeks(id: ID, modelFxn: (ID?) -> M) {
    describe("isPersisted") {
        context("with an id set") {
            val model by memoized { modelFxn(id) }

            it("is persisted") {
                model should satisfy { isPersisted() }
            }
        }

        context("with no id set") {
            val id by memoized { null }
            val model by memoized { modelFxn(null) }

            it("is persisted") {
                model shouldNot satisfy { isPersisted() }
            }
        }
    }
}
