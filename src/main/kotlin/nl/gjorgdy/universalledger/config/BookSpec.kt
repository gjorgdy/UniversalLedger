package nl.gjorgdy.universalledger.config

import com.uchuhimo.konf.ConfigSpec

object BookSpec : ConfigSpec() {
    val areaActions by optional(setOf("item-pick-up", "item-drop", "entity-kill", "entity-change", "entity-mount", "entity-dismount"))
    val inventoryActions by optional(setOf("item-insert", "item-remove"))
    val blockActions by optional(setOf("block-place", "block-break", "block-change"))
}