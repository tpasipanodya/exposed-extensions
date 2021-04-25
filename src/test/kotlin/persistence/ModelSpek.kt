package persistence

import com.taff.hephaestustest.expectation.boolean.beTrue
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.env
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.tables.ModelAwareTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

data class Record(val title: String? = null,
                  override var id: Long? = null,
                  override var createdAt: OffsetDateTime? = null,
                  override var updatedAt: OffsetDateTime? = null) : Model {
}

val records = object : ModelAwareTable<Record>("records") {
    val title = varchar("title", 50)
    override fun initializeModel(row: ResultRow) = Record(title = row[title])
    override fun fill(stmt: UpdateBuilder<Int>, model: Record) {
        model.title?.let { stmt[title] = it }
    }
}

object ModelSpek  : Spek({

    Database.connect(env<String>("DB_URL"))
    transaction { SchemaUtils.create(records) }

    beforeEachTest { records.deleteAll() }

    describe("insert") {
        val record by memoized { Record("Soul food") }

        beforeEachTest { transaction { records.insert(record) } }

        it("persists") {
            record.isPersisted() should beTrue()
        }
    }
})