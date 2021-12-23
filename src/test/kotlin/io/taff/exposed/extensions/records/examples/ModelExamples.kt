package io.taff.exposed.extensions.records.examples

import io.taff.exposed.extensions.records.Record
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import io.taff.spek.expekt.shouldNot
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.style.specification.describe

fun <ID : Comparable<ID>, M : Record<ID>> Root.includeRecordSpeks(id: ID, recordFxn: (ID?) -> M) {
    describe("isPersisted") {
        context("with an id set") {
            val record by memoized { recordFxn(id) }

            it("is persisted") {
                record should satisfy { isPersisted() }
            }
        }

        context("with no id set") {
            val record by memoized { recordFxn(null) }

            it("is persisted") {
                record shouldNot satisfy { isPersisted() }
            }
        }
    }
}
