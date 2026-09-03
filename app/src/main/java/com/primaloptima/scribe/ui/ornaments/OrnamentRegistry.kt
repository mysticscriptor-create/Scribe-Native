package com.primaloptima.scribe.ui.ornaments

/**
 * Registry containing all available manuscript ornament styles.
 *
 * Extensibility:
 * To add a new ornament:
 * 1. Implement [ManuscriptOrnament] in this folder.
 * 2. Add it to the [all] list below.
 *
 * To remove an ornament, simply remove it from the [all] list.
 */
object OrnamentRegistry {
    val all: List<ManuscriptOrnament> = listOf(
        ClassicDiamondOrnament,
        FleuronOrnament,
        AsterismOrnament,
        CelticKnotOrnament,
        MinimalRuleOrnament,
        GothicQuatrefoilOrnament,
        LaurelBranchOrnament,
        NoneOrnament
    )

    fun getById(id: String): ManuscriptOrnament {
        return all.find { it.id == id } ?: ClassicDiamondOrnament
    }
}
