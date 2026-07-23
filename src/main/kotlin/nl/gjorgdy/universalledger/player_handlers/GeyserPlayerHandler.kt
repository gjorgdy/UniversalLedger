package nl.gjorgdy.universalledger.player_handlers

import net.minecraft.server.level.ServerPlayer
import org.geysermc.geyser.api.GeyserApi

class GeyserPlayerHandler : PlayerHandler {

    override fun isBedrockPlayer(player: ServerPlayer): Boolean {
        return GeyserApi.api().isBedrockPlayer(player.uuid)
    }

}