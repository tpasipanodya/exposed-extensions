package io.taff.exposed.extensions.records

import io.taff.exposed.extensions.records.examples.includeRecordSpeks
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import io.taff.spek.expekt.shouldNot
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.Instant.now
import java.util.*


data class MySoftDeletableRecord(
    override var id: UUID?,
    override var softDeletedAt: Instant?,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : SoftDeletableRecord<UUID>

object SoftDeletableRecordSpek : Spek ({

    includeRecordSpeks(UUID.randomUUID()) {
        MySoftDeletableRecord(id = it, softDeletedAt = null)
    }

    describe("isSoftDeleted") {
        val id by memoized { UUID.randomUUID() }

        context("no softDeletedAt value set") {
            val record by memoized { MySoftDeletableRecord(id, null) }

            it("is not soft deleted") {
                record shouldNot satisfy { isSoftDeleted() }
            }
        }

        context("softDeletedAt value set") {
            val softDeletedAt by memoized { now() }
            val record by memoized { MySoftDeletableRecord(id, softDeletedAt) }

            it("is not soft deleted") {
                record should satisfy { isSoftDeleted() }
            }
        }

    }

    describe("markAsSoftDeleted") {
        val id by memoized { UUID.randomUUID() }
        val record by memoized { MySoftDeletableRecord(id, null) }

        it("marks the record as soft deleted") {
            record shouldNot satisfy { isSoftDeleted() }
            record.markAsSoftDeleted()
            record should satisfy { isSoftDeleted() }
        }
    }
})
