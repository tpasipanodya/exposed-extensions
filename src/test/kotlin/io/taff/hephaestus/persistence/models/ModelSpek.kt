package io.taff.hephaestus.persistence.models

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import com.taff.hephaestustest.expectation.shouldNot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

data class MyModel(
    override var id: UUID?,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null
) : Model

object ModelSpek : Spek({

    describe("isPersisted") {
        context("with an id set") {
            val id by memoized { UUID.randomUUID() }
            val model by memoized { MyModel(id) }

            it("is persisted") {
                model should satisfy { isPersisted() }
            }
        }

        context("with no id set") {
            val id by memoized { null }
            val model by memoized { MyModel(id) }

            it("is persisted") {
                model shouldNot satisfy { isPersisted() }
            }
        }
    }

})