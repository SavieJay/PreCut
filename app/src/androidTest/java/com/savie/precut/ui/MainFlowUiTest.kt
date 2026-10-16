package com.savie.precut.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.savie.precut.data.ProjectSummary
import com.savie.precut.export.ExportFailure
import com.savie.precut.model.OutputSize
import com.savie.precut.model.export.ExportCapabilities
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.ui.editor.export.ExportSettingsSheet
import com.savie.precut.ui.editor.export.ExportSheetActions
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.ToolbarActions
import com.savie.precut.ui.export.ExportActions
import com.savie.precut.ui.export.ExportContent
import com.savie.precut.ui.export.ExportInfo
import com.savie.precut.ui.export.ExportRecovery
import com.savie.precut.ui.export.ExportUiState
import com.savie.precut.ui.home.HomeContent
import com.savie.precut.ui.home.ProjectActions
import com.savie.precut.ui.theme.PreCutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** The main flow's screens (create → edit → export), driven like a user would. */
@RunWith(AndroidJUnit4::class)
class MainFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    /** Records which interface methods were called, with their arguments. */
    private class Calls {
        val made = mutableListOf<Pair<String, List<Any?>>>()
        inline fun <reified T> recorder(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                // Compose compares and hashes the actions object; those aren't user actions.
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "recorder"
                else -> {
                    made += method.name to args.orEmpty().toList()
                    if (method.returnType == Boolean::class.javaPrimitiveType) false else null
                }
            }
        } as T
        fun names() = made.map { it.first }
    }

    @Test
    fun projects_emptyStateOffersANewProject_andDraftsOpen() {
        var newProject = 0
        val calls = Calls()
        var projects by mutableStateOf<List<ProjectSummary>>(emptyList())
        compose.setContent {
            PreCutTheme {
                HomeContent(false, projects, SnackbarHostState(), onNewProject = { newProject++ }, onCamera = {}, actions = calls.recorder<ProjectActions>())
            }
        }
        compose.onNodeWithText("Start your first video").assertIsDisplayed()
        compose.onNodeWithText("New project").performClick()
        assertEquals(1, newProject)

        projects = listOf(ProjectSummary("p1", "Lagos weekend", null, 18_000L, 0L, 0L))
        compose.onNodeWithText("Lagos weekend").performClick()
        assertEquals("onOpen" to listOf<Any?>("p1"), calls.made.single())
    }

    @Test
    fun editorToolbar_reachesTextStickersAndEffects() {
        val calls = Calls()
        compose.setContent { PreCutTheme { EditorToolbar(canSplit = true, hasClips = true, actions = calls.recorder<ToolbarActions>()) } }
        listOf("Effects", "Text", "Stickers", "Draw").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        assertEquals(listOf("onEffects", "onText", "onStickers", "onDraw"), calls.names())
    }

    @Test
    fun exportSheet_disablesWhatThePhoneCantEncode_andExports() {
        val calls = Calls()
        var settings by mutableStateOf(ExportSettings())
        compose.setContent {
            PreCutTheme {
                ExportSettingsSheet(
                    settings = settings, capabilities = ExportCapabilities.BASIC, outputSize = OutputSize(1080, 1920),
                    durationMs = 18_000L, savingFrame = false, actions = calls.recorder<ExportSheetActions>(),
                )
            }
        }
        compose.onNodeWithText("4K").assertIsNotEnabled()
        compose.onNodeWithText("4K: Not supported on this device").assertIsDisplayed()
        compose.onNodeWithText("720p").performClick()
        assertEquals("onSettings" to listOf<Any?>(ExportSettings(resolution = ExportResolution.P720)), calls.made.last())
        compose.onNodeWithText("Export video").performClick()
        compose.onNodeWithText("Audio only").performClick()
        assertTrue(calls.names().containsAll(listOf("onExportVideo", "onAudioOnly")))
    }

    @Test
    fun exportScreens_progressDoneAndAnEncoderFailure() {
        val calls = Calls()
        var state by mutableStateOf<ExportUiState>(ExportUiState.Exporting(64))
        val info = ExportInfo("Lagos weekend", 0L, 18_000L, null, ExportSettings(resolution = ExportResolution.P4K))
        compose.setContent {
            PreCutTheme {
                ExportContent(state, info, calls.recorder<ExportActions>(), ExportRecovery(ExportResolution.P4K, ExportResolution.P2K, 0L))
            }
        }
        compose.onNodeWithText("64%").assertIsDisplayed()
        compose.onNodeWithText("Exporting “Lagos weekend”").assertIsDisplayed()

        state = ExportUiState.Done("content://saved")
        compose.onNodeWithText("Saved to Movies/PreCut").assertIsDisplayed()
        compose.onNodeWithText("Share").performClick()
        assertEquals("onShare" to listOf<Any?>("content://saved"), calls.made.last())

        state = ExportUiState.Failed(ExportFailure.ENCODER)
        compose.onNodeWithText("Try 2K").performClick()
        assertEquals("onTryLower", calls.made.last().first)
        compose.onNodeWithText("Back to edit").assertIsDisplayed()
    }
}
