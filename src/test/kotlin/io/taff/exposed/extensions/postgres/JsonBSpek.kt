package io.taff.exposed.extensions.postgres

import com.fasterxml.jackson.core.type.TypeReference
import io.taff.exposed.extensions.Config
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

data class RecordWithJson(
    override var id: UUID? = null,
    var json: Map<String, Any> = mapOf(),
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Record<UUID>


val recordsWithJson = object : RecordMappingUuidTable<RecordWithJson>("records_with_json") {
    val json = jsonb("strings") {
        Config.objectMapper.readValue(
            it,
            object  : TypeReference<Map<String, Any>>(){}
        )
    }

    override fun initializeRecord(row: ResultRow) = RecordWithJson(json = row[json])

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: RecordWithJson) {
        stmt[json] = record.json
    }
}

object JsonBSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(recordsWithJson) }

    beforeEachTest { transaction { recordsWithJson.deleteAll() } }

    describe("reading and writing") {
        val actualJson by memoized {
            mapOf("A" to "B",
                "C" to mapOf("D" to "E"))
        }
        val persisted by memoized {
            transaction {
                recordsWithJson
                    .insert(RecordWithJson(json = actualJson))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                recordsWithJson
                    .selectAll()
                    .map(recordsWithJson::toRecord)
                    .first()
            }
        }

        it("correctly persists and loads the record") {
            persisted should satisfy {
                json == actualJson &&
                !(id.isNull() &&
                createdAt.isNull() &&
                updatedAt.isNull())
            }
            reloaded should satisfy {
                json == actualJson &&
                id == persisted.id &&
                !(createdAt.isNull() &&
                updatedAt.isNull())
            }
        }
    }
})
