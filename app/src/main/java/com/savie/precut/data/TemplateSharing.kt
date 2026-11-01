package com.savie.precut.data

import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.template.TemplateLinks
import com.savie.precut.model.template.forSharing
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Why a template can't be shared. */
sealed interface ShareResult {
    data class Shared(val url: String) : ShareResult
    /** It holds photos or stickers of the maker's own, which a link can't carry. */
    data object HasOwnMedia : ShareResult
    data class Failed(val failure: AiFailure) : ShareResult
}

/** Template links (Phase 28): sharing a template, and opening a shared one. */
interface TemplateSharing {
    suspend fun share(template: ProjectTemplate): ShareResult

    /** Fetches the template behind link [id] and keeps it in Projects → Templates; its local id. */
    suspend fun open(id: String): AiResult<String>
}

@Singleton
class CloudTemplateSharing @Inject constructor(
    private val cloud: CloudClient,
    private val store: TemplateStore,
    private val web: WebConfig,
) : TemplateSharing {
    override suspend fun share(template: ProjectTemplate): ShareResult {
        val shared = template.forSharing() ?: return ShareResult.HasOwnMedia
        val json = Json.parseToJsonElement(TemplateSerializer.encode(shared))
        return when (val result = cloud.shareTemplate(json)) {
            is AiResult.Ok -> ShareResult.Shared(TemplateLinks.link(web.host, result.value))
            is AiResult.Failed -> ShareResult.Failed(result.failure)
        }
    }

    override suspend fun open(id: String): AiResult<String> {
        val localId = TemplateLinks.localId(id)
        if (store.get(localId) != null) return AiResult.Ok(localId)
        return when (val result = cloud.openTemplate(id)) {
            is AiResult.Failed -> result
            is AiResult.Ok -> {
                val template = TemplateSerializer.decode(result.value.toString()) ?: return AiResult.Failed(AiFailure.UpdateApp)
                store.save(template.copy(id = localId, project = template.project.copy(id = localId)))
                AiResult.Ok(localId)
            }
        }
    }
}

/** The website PreCut's links point at (from cloud.properties' `webHost`). */
data class WebConfig(val host: String)
