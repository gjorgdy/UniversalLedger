package nl.gjorgdy.universalledger.player_handlers

import net.minecraft.server.level.ServerPlayer

interface PlayerHandler {
    fun isBedrockPlayer(player: ServerPlayer): Boolean
}