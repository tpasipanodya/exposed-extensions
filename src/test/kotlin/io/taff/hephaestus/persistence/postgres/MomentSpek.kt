package io.taff.hephaestus.persistence.postgres

import com.fasterxml.jackson.core.type.TypeReference
import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.Config
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.ModelMappingTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.days

data class ModelWithMoment(override var id: UUID? = null,
                         var moment: OffsetDateTime = OffsetDateTime.now(),
                         override var createdAt: OffsetDateTime? = null,
                         override var updatedAt: OffsetDateTime? = null) : Model


val modelsWithAMoment = object : ModelMappingTable<ModelWithMoment>("models_with_a_moment") {
    val moment = moment("moment")

    override fun initializeModel(row: ResultRow) = ModelWithMoment(moment = row[moment])

    override fun fillStatement(stmt: UpdateBuilder<Int>, model: ModelWithMoment) {
        stmt[moment] = model.moment
        super.fillStatement(stmt, model)
    }
}

object MomentSpek : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(modelsWithAMoment) }

    beforeEachTest { transaction { modelsWithAMoment.deleteAll() } }

    describe("reading and writing") {
        val actualMoment by memoized {
            OffsetDateTime
                .now()
                .let { it.minusNanos(it.nano.toLong()) }
        }
        val persisted by memoized {
            transaction {
                modelsWithAMoment
                    .insert(ModelWithMoment(moment = actualMoment))
                    .first()
            }
        }
        val reloaded by memoized {
            transaction {
                modelsWithAMoment
                    .selectAll()
                    .map(modelsWithAMoment::toModel)
                    .first()
            }
        }

        it("correctly persists and loads the model") {
            persisted should satisfy {
                moment == actualMoment &&
                !(id.isNull() &&
                createdAt.isNull() &&
                updatedAt.isNull())
            }
            reloaded should satisfy {
                moment == actualMoment &&
                id == persisted.id &&
                !(createdAt.isNull() &&
                updatedAt.isNull())
            }
        }
    }
})