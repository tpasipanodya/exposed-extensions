package io.taff.hephaestus.persistence.models

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import com.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe
import java.util.*

fun <M : Model> Root.includeModelSpeks(modelFxn: (id: UUID?) -> M) {
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