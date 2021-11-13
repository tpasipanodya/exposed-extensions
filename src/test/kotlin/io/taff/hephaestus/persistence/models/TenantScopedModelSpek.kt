package io.taff.hephaestus.persistence.models

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.CurrentTenantId
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

data class MyTenantScopedModel(
    override var id: UUID?,
    override var tenantId: UUID?,
    override var createdAt: OffsetDateTime? = null,
    override var updatedAt: OffsetDateTime? = null
) : TenantScopedModel

object TenantScopedModelSpek : Spek({

    val tenantId by memoized { UUID.randomUUID() }
    val model by memoized {
        MyTenantScopedModel(
            id = UUID.randomUUID(),
            tenantId = tenantId
        )
    }

    includeModelSpeks {
        MyTenantScopedModel(
            id = it,
            tenantId = tenantId
        )
    }

    describe("asCurrent (returning T)") {
        it("sets tenant id and returns the lambda's result") {
            var currentTenant : UUID? = null

            model.asTenant<String> {
                currentTenant = CurrentTenantId.get()
                "foo"
            } should satisfy  { this == "foo" }

            currentTenant should satisfy { this == tenantId }
        }
    }

    describe("asCurrent (returning void)") {
        it("sets tenant id and returns the lambda's result") {
            var currentTenant : UUID? = null

            model.asTenant { currentTenant = CurrentTenantId.get() }

            currentTenant should satisfy { this == tenantId }
        }
    }
})