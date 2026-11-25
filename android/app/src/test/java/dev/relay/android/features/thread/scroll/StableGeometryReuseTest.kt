package dev.relay.android.features.thread.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StableGeometryReuseTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aHeldRowThatFoldedLeavesNoGapWhenItComesBack() {
        val list = LazyListState()
        val geometry = StableGeometryState()
        var header by mutableStateOf(90)
        compose.setContent {
            LazyColumn(Modifier.height(400.dp), list) {
                item("header") {
                    Box(Modifier.fillMaxWidth().stableGeometry(geometry, holding = true)) { Box(Modifier.height(header.dp)) }
                }
                items(40) { Box(Modifier.fillMaxWidth().height(100.dp)) }
            }
        }
        fun gapOverFirstRow() = compose.runOnIdle { list.layoutInfo.visibleItemsInfo.first { it.index == 1 }.offset }
        fun show(index: Int) = compose.runOnIdle { list.requestScrollToItem(index) }
        val held = gapOverFirstRow()

        header = 0
        assertEquals("held while in view", held, gapOverFirstRow())
        show(30)
        show(0)

        assertEquals(0, gapOverFirstRow())
    }
}
