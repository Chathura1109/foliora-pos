package com.foliora.pos

import com.foliora.pos.data.local.entity.priceToCents
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun priceToCents_normalizesEquivalentPrices() {
        assertEquals(priceToCents(100.50), priceToCents(100.5000001))
    }

    @Test
    fun priceToCents_detectsARealPriceChange() {
        assertNotEquals(priceToCents(100.50), priceToCents(100.51))
    }
}
