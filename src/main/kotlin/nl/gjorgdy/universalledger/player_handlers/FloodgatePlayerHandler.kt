package nl.gjorgdy.universalledger.player_handlers

import net.minecraft.server.level.ServerPlayer
import org.geysermc.floodgate.api.FloodgateApi

class FloodgatePlayerHandler : PlayerHandler {

    override fun isBedrockPlayer(player: ServerPlayer): Boolean {
        return FloodgateApi.getInstance().isFloodgatePlayer(player.uuid)
    }

}