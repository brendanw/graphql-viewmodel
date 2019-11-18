package com.basebeta.envoycoffee

import com.basebeta.envoycoffee.base.Key
import com.basebeta.envoycoffee.main.MainViewState
import com.basebeta.envoycoffee.main.YelpResult
import org.junit.Test

import org.junit.Assert.*
import java.util.*

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
    fun keyEntry_isRemoved() {
        val weakMap = WeakHashMap<Key, Any>()
        var key: Key? = Key(key = "abc")
        weakMap[key] = "arbitrary"
        assertTrue(weakMap.size == 1)
        key = null
        System.gc()
        Thread.sleep(5000)
        assertTrue(weakMap.size == 0)
    }

    @Test
    fun modelComparision_isCorrect() {
        var modelA = MainViewState(shopList = emptyList(), oldShopList = emptyList(), diffResult = null, showNetworkError = false)
        var modelB = MainViewState(shopList = emptyList(), oldShopList = emptyList(), diffResult = null, showNetworkError = false)
        assert(modelA == modelB)
        modelB = MainViewState(shopList = emptyList(), oldShopList = emptyList(), diffResult = null, showNetworkError = true)
        assert(modelA != modelB)

        var listA = listOf(
            YelpResult("Blue bottle", "bluebottle.com/image.png", "500 gough", "$")
        )
        var listB = listOf(
            YelpResult("Blue bottle", "bluebottle.com/image.png", "500 gough", "$")
        )
        assert(listA == listB)

        listB = listOf(
            YelpResult("Blue bottle", "bluebottle.com/image.png", "500 gough", "$"),
            YelpResult("Centro", "bluebottle.com/image.png", "500 gough", "$")
        )
        assert(listA != listB)

        listB = listOf(
            YelpResult("Centro", "bluebottle.com/image.png", "500 gough", "$")
        )
        assert(listA != listB)
    }
}
