package com.savie.precut.evals

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.savie.precut.model.ai.edit.EditEnd
import com.savie.precut.model.ai.edit.EditSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * AI Edit's evals (docs/AGENT_EVALS.md): the 20 cases against the real Claude, with the app's own
 * session and tools. They cost money, so they run only when asked:
 * `PRECUT_RUN_EVALS=1 ANTHROPIC_API_KEY=… ./gradlew testDebugUnitTest --tests '*AgentEvalsTest'`
 * (ANTHROPIC_MODEL picks another model). The report goes to app/build/reports/agent-evals.md.
 */
class AgentEvalsTest {

    @Test
    fun `at least 17 of the 20 evals pass`() {
        assumeTrue(System.getenv("PRECUT_RUN_EVALS") == "1" && !System.getenv("ANTHROPIC_API_KEY").isNullOrBlank())
        val model = System.getenv("ANTHROPIC_MODEL")?.takeIf { it.isNotBlank() } ?: "claude-opus-5-5"
        val client = AnthropicOkHttpClient.fromEnv()
        val rows = mutableListOf<String>()
        var passed = 0
        var input = 0L
        var output = 0L
        EVAL_CASES.forEach { case ->
            val step = ClaudeEvalStep(client, model)
            var ids = 0
            val session = EditSession(case.project, { "id${++ids}" }, { _, messages -> step.step(messages) })
            val end = runBlocking { session.ask(case.prompt, "eval-${case.id}", 0L, emptySet(), case.media) {} }
            val message = (end as? EditEnd.Done)?.message ?: (end as? EditEnd.Stopped)?.message.orEmpty()
            val failures = case.check(EvalOutcome(case.project, session.proposal, session.changes.map { it.label }, session.rejected, message)) +
                listOfNotNull((end as? EditEnd.Failed)?.let { "failed: ${it.failure}" })
            if (failures.isEmpty()) passed++
            input += step.inputTokens
            output += step.outputTokens
            rows += "| ${case.id} | ${case.prompt} | ${if (failures.isEmpty()) "pass" else "FAIL: " + failures.joinToString("; ")} | ${session.changes.size} | ${session.rejected} |"
        }
        val report = buildString {
            appendLine("# AI Edit evals ($model)")
            appendLine()
            appendLine("$passed of ${EVAL_CASES.size} passed. Tokens: $input in, $output out.")
            appendLine()
            appendLine("| # | Request | Result | Changes | Rejected calls |")
            appendLine("|---|---|---|---|---|")
            rows.forEach(::appendLine)
        }
        File("build/reports").mkdirs()
        File("build/reports/agent-evals.md").writeText(report)
        println(report)
        assertTrue("$passed of ${EVAL_CASES.size} passed; see build/reports/agent-evals.md", passed >= 17)
    }
}
