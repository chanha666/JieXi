package com.yunx.desktop

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class DesktopBrandResourcesTest {
    @Test
    fun `legacy compose icon stays identical to the current brand mark`() {
        val loader = DesktopBrandResourcesTest::class.java.classLoader
        val brand = assertNotNull(loader.getResourceAsStream("icon_brand.png")).use { it.readBytes() }
        val compose = assertNotNull(
            loader.getResourceAsStream("composeResources/jiexi.desktopapp.generated.resources/drawable/icon.png")
        ).use { it.readBytes() }

        assertContentEquals(brand, compose)
    }
}
