package io.taff.exposed.extensions.postgres

import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.records.Record
import io.taff.exposed.extensions.tables.uuid.RecordMappingUuidTable
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.util.*

data class RecordWithArrays(
    override var id: UUID? = null,
    var strings: List<String> = listOf(),
    var longs: List<Long> = listOf(),
    var ints: List<Int> = listOf(),
    var bools: List<Boolean> = listOf(),
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Record<UUID>


val recordsWithArrays = object : RecordMappingUuidTable<RecordWithArrays>("records_with_arrays") {
    val strings = stringArray("strings")
    val ints = intArray("ints")
    val longs = longArray("longs")
    val bools = boolArray("bools")

    override fun initializeRecord(row: ResultRow) = RecordWithArrays(
        strings = row[strings],
        longs = row[longs],
        ints = row[ints],
        bools = row[bools]
    )

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: RecordWithArrays) {
        stmt[strings] = record.strings
        stmt[longs] = record.longs
        stmt[ints] = record.ints
        stmt[bools] = record.bools
    }
}

object ArraySpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(recordsWithArrays) }

    beforeEachTest { transaction { recordsWithArrays.deleteAll() } }

    describe("reading and writing") {
        val actualStrings by memoized { listOf("foo", "bar") }
        val actualInts by memoized { listOf(10, 100) }
        val actualLongs by memoized { listOf(1_000L, 10_000L) }
        val actualBools by memoized { listOf(false, true) }
        val persisted by memoized {
            transaction {
                recordsWithArrays.insert(RecordWithArrays(
                    strings = actualStrings,
                    ints = actualInts,
                    longs = actualLongs,
                    bools = actualBools,
                )).first()
            }
        }
        val reloaded by memoized {
            transaction {
                recordsWithArrays
                    .selectAll()
                    .map(recordsWithArrays::toRecord)
                    .first()
            }
        }

        it("correctly persists and loads the record") {
            persisted should satisfy {
                strings == actualStrings &&
                ints == actualInts &&
                longs == actualLongs &&
                bools == actualBools &&
                !(createdAt.isNull() &&
                updatedAt.isNull() &&
                id.isNull())
            }

            reloaded should satisfy {
                strings == actualStrings &&
                ints == actualInts &&
                longs == actualLongs &&
                bools == actualBools &&
                !(createdAt.isNull() &&
                updatedAt.isNull()) &&
                persisted.id == reloaded.id
            }
        }
    }
})
