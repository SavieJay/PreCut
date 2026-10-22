package com.savie.precut.model.sticker

/** One sticker in a pack: its file name and the emoji it shows (its label on the timeline). */
data class PackSticker(val name: String, val emoji: String)

/**
 * Sticker packs that ship with the app (Phase 19): square pictures in
 * `assets/stickers/<id>/<name>.webp`, made from openly licensed emoji sets, credited in Settings →
 * Open-source licences and on the pack's tab. A placed one is a [StickerKind.PACK] sticker whose
 * value is `<id>/<name>`, so it never counts as the project's own media.
 */
enum class StickerPack(val id: String, val animated: Boolean, val stickers: List<PackSticker>) {
    /** Animated Noto Emoji (Google, CC BY 4.0), 192 px at 15 fps. */
    ANIMATED(
        "noto_animated", animated = true,
        listOf(
            "joy" to "😂", "heart_eyes" to "😍", "smiling_hearts" to "🥰", "star_struck" to "🤩", "partying" to "🥳",
            "sunglasses" to "😎", "sobbing" to "😭", "screaming" to "😱", "thinking" to "🤔", "eye_roll" to "🙄",
            "angry" to "😡", "mind_blown" to "🤯", "fire" to "🔥", "hundred" to "💯", "red_heart" to "❤️",
            "party_popper" to "🎉", "clapping" to "👏", "thumbs_up" to "👍", "folded_hands" to "🙏", "flexed_biceps" to "💪",
            "sparkles" to "✨", "eyes" to "👀", "skull" to "💀", "rocket" to "🚀",
        ).map { (name, emoji) -> PackSticker(name, emoji) },
    ),

    /** Fluent Emoji in their 3D style (Microsoft, MIT), 256 px. */
    THREE_D(
        "fluent_3d", animated = false,
        listOf(
            "grinning_face" to "😀", "face_with_tears_of_joy" to "😂", "smiling_face_with_heart_eyes" to "😍",
            "smiling_face_with_sunglasses" to "😎", "star_struck" to "🤩", "partying_face" to "🥳", "thinking_face" to "🤔",
            "face_screaming_in_fear" to "😱", "loudly_crying_face" to "😭", "pleading_face" to "🥺", "exploding_head" to "🤯",
            "zany_face" to "🤪", "money_mouth_face" to "🤑", "cowboy_hat_face" to "🤠", "smiling_face_with_hearts" to "🥰",
            "face_blowing_a_kiss" to "😘", "thumbs_up" to "👍", "clapping_hands" to "👏", "folded_hands" to "🙏",
            "waving_hand" to "👋", "ok_hand" to "👌", "victory_hand" to "✌️", "flexed_biceps" to "💪", "raising_hands" to "🙌",
            "red_heart" to "❤️", "sparkling_heart" to "💖", "fire" to "🔥", "hundred_points" to "💯", "sparkles" to "✨",
            "star" to "⭐", "glowing_star" to "🌟", "high_voltage" to "⚡", "collision" to "💥", "check_mark_button" to "✅",
            "cross_mark" to "❌", "red_exclamation_mark" to "❗", "party_popper" to "🎉", "confetti_ball" to "🎊",
            "birthday_cake" to "🎂", "wrapped_gift" to "🎁", "balloon" to "🎈", "trophy" to "🏆", "crown" to "👑",
            "money_bag" to "💰", "rocket" to "🚀", "camera_with_flash" to "📸", "musical_notes" to "🎶", "rainbow" to "🌈",
        ).map { (name, emoji) -> PackSticker(name, emoji) },
    ),
    ;

    /** The sticker to place for [item]. */
    fun sticker(item: PackSticker): Sticker = Sticker(StickerKind.PACK, "$id/${item.name}", animated = animated)

    /** Where [item]'s picture is in the app's assets. */
    fun assetPath(item: PackSticker): String = "$ASSET_DIR/$id/${item.name}.webp"

    companion object {
        const val ASSET_DIR = "stickers"

        /** The pack and item a [StickerKind.PACK] sticker shows; null for any other sticker or an unknown one. */
        fun of(sticker: Sticker): Pair<StickerPack, PackSticker>? {
            if (sticker.kind != StickerKind.PACK) return null
            val pack = entries.firstOrNull { sticker.value.startsWith(it.id + "/") } ?: return null
            val item = pack.stickers.firstOrNull { it.name == sticker.value.substringAfter('/') } ?: return null
            return pack to item
        }
    }
}
