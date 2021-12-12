package io.taff.exposed.extensions

import io.taff.spek.expekt.any.equal
import io.taff.spek.expekt.any.satisfy
import io.taff.spek.expekt.should
import kotlinx.coroutines.asContextElement
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FunctionsSpek  : Spek({

    describe("isNull") {
        val nullValue by memoized { null }
        val nonNullValue by memoized { 1L }

        it("correctly checks for nulls") {
            nullValue.isNull() should equal(true)
            nonNullValue.isNull() should equal(false)
        }
    }

    describe("currentTenantId, setCurrentTenantId & clearCurrentTenantId") {
        it("returns the current tenant id") {
            currentTenantId<Long?>() should satisfy { isNull() }

            setCurrentTenantId(1L)
            currentTenantId<Long?>() should equal(1L)

            setCurrentTenantId(2L)
            currentTenantId<Long?>() should equal(2L)

            clearCurrentTenantId<Long?>()
            currentTenantId<Long?>() should satisfy { isNull() }
        }
    }
})
