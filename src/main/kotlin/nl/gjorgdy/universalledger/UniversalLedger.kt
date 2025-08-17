package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.AbstractActionType
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.actionutils.SearchResults
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.Negatable
import com.github.quiltservertools.ledger.utility.TextColorPallet
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.WrittenBookContentComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.MutableText
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockBox
import kotlin.time.ExperimentalTime

class UniversalLedger : ModInitializer {

    @OptIn(ExperimentalTime::class)
    override fun onInitialize() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (player.getStackInHand(hand).isOf(Items.KNOWLEDGE_BOOK)) {
                // Define parameters for the ledger search
                val params = ActionSearchParams.build {
                    this.worlds = mutableSetOf(Negatable.allow(world.registryKey.value))
                    this.bounds = BlockBox.create(hitResult.blockPos, hitResult.blockPos)
                }
                Ledger.launch {
                    // Get the actions for the selected block
                    val actions = getActions(params, 2)
                    // Map the retrieved actions to text objects
                    val actionTexts = actions.map { it.getText(player.getCommandSource(world as ServerWorld?)) }
                    // Paginate the lines
                    val pages = paginateLines(actionTexts, 4)
                    // Create a book from the pages
                    val book = createBook(pages)
                    // Open the book to the player
                    openBook(player as ServerPlayerEntity, book)
                }
                return@register ActionResult.CONSUME
            }
            return@register ActionResult.PASS
        }
    }

    suspend fun getActions(params: ActionSearchParams, pages: Int): List<ActionType> {
        val actions = mutableListOf<ActionType>()
        for (i in 1..pages) {
            val results: SearchResults = DatabaseManager.searchActions(params, i)
            actions.addAll(results.actions)
        }
        return actions
    }

    @OptIn(ExperimentalTime::class)
    fun ActionType.getText(source: ServerCommandSource): MutableText {
        val aat = this as AbstractActionType
        val text = Text.translatable(
            "text.ledger.action_message",
            aat.getTimeMessage(),
            aat.getSourceMessage(),
            Text.literal("\n"),
            aat.getActionMessage(),
            aat.getObjectMessage(source)
        )
        if (aat.rolledBack) {
            text.formatted(Formatting.STRIKETHROUGH)
        }
        return text
    }

    fun paginateLines(actionTexts: List<MutableText>, actionsPerPage: Int): List<RawFilteredPair<Text>> {
        var page = Text.empty()
        val pages = mutableListOf<RawFilteredPair<Text>>()
        for (i in actionTexts.indices) {
            val text = actionTexts[i]
            text.style =
                if (i % 2 == 0) TextColorPallet.primary
                else TextColorPallet.primaryVariant
            page = page.append(text)
            page = page.append("\n\n")
            if ((i % actionsPerPage) == (actionsPerPage - 1)) {
                pages.add(RawFilteredPair.of(page))
                page = Text.empty()
            }
        }
        return pages
    }

    fun createBook(pages: List<RawFilteredPair<Text>>): ItemStack {
        val bookComponent = WrittenBookContentComponent(
            RawFilteredPair.of("Ledger"),
            "",
            0,
            pages,
            true
        )
        val book = Items.WRITTEN_BOOK.defaultStack
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookComponent)
        return book
    }

    fun openBook(player: ServerPlayerEntity, book: ItemStack) {
        player.networkHandler
            .sendPacket(SetPlayerInventoryS2CPacket(40, book))
        player.useBook(book, Hand.OFF_HAND)
        player.networkHandler
            .sendPacket(SetPlayerInventoryS2CPacket(40, player.inventory.getStack(40)))
    }

}
