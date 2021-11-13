package io.taff.hephaestus.persistence.postgres

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.uuid.ModelMappingUuidTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

data class ModelWithArrays(
    override var id: UUID? = null,
    var strings: List<String> = listOf(),
    var longs: List<Long> = listOf(),
    var ints: List<Int> = listOf(),
    var bools: List<Boolean> = listOf(),
    var doubles: List<Double> = listOf(),
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null
) : Model<UUID>


val modelsWithArrays = object : ModelMappingUuidTable<ModelWithArrays>("models_with_arrays") {
    val strings = stringArray("strings")
    val ints = intArray("ints")
    val longs = longArray("longs")
    val bools = boolArray("bools")

    override fun initializeModel(row: ResultRow) = ModelWithArrays(
        strings = row[strings],
        longs = row[longs],
        ints = row[ints],
        bools = row[bools]
    )

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: ModelWithArrays) {
        stmt[strings] = model.strings
        stmt[longs] = model.longs
        stmt[ints] = model.ints
        stmt[bools] = model.bools
    }
}

object ArraySpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(modelsWithArrays) }

    beforeEachTest { transaction { modelsWithArrays.deleteAll() } }

    describe("reading and writing") {
        val actualStrings by memoized { listOf("foo", "bar") }
        val actualInts by memoized { listOf(10, 100) }
        val actualLongs by memoized { listOf(1_000L, 10_000L) }
        val actualBools by memoized { listOf(false, true) }
        val persisted by memoized {
            transaction {
                modelsWithArrays.insert(ModelWithArrays(
                    strings = actualStrings,
                    ints = actualInts,
                    longs = actualLongs,
                    bools = actualBools,
                )).first()
            }
        }
        val reloaded by memoized {
            transaction {
                modelsWithArrays
                    .selectAll()
                    .map(modelsWithArrays::toModel)
                    .first()
            }
        }

        it("correctly persists and loads the model") {
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