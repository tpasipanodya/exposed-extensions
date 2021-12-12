package io.taff.exposed.extensions.postgres

import com.fasterxml.jackson.core.type.TypeReference
import io.taff.exposed.extensions.Config
import io.taff.exposed.extensions.env
import io.taff.exposed.extensions.isNull
import io.taff.exposed.extensions.models.Model
import io.taff.exposed.extensions.tables.uuid.ModelMappingUuidTable
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.util.*

data class ModelWithJson(
    override var id: UUID? = null,
    var json: Map<String, Any> = mapOf(),
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null
) : Model<UUID>


val modelsWithJson = object : ModelMappingUuidTable<ModelWithJson>("models_with_json") {
    val json = jsonb("strings") {
        Config.objectMapper.readValue(
            it,
            object  : TypeReference<Map<String, Any>>(){}
        )
    }

    override fun initializeModel(row: ResultRow) = ModelWithJson(json = row[json])

    override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: ModelWithJson) {
        stmt[json] = model.json
    }
}

object JsonBSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(modelsWithJson) }

    beforeEachTest { transaction { modelsWithJson.deleteAll() } }

    describe("reading and writing") {
        val actualJson by memoized {
            mapOf("A" to "B",
                "C" to mapOf("D" to "E"))
        }
        val persisted by memoized {
            transaction {
                modelsWithJson
                    .insert(ModelWithJson(json = actualJson))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                modelsWithJson
                    .selectAll()
                    .map(modelsWithJson::toModel)
                    .first()
            }
        }

        it("correctly persists and loads the model") {
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
