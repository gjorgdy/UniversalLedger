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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
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
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Colors
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import kotlin.time.ExperimentalTime

class UniversalLedger : ModInitializer {

    @OptIn(ExperimentalTime::class)
    override fun onInitialize() {
        // when right-clicking in the air, ledger events in an 8-block radius
        UseItemCallback.EVENT.register { player, world, hand ->
            if (isLedgerBook(player.getStackInHand(hand))) {
                ledgerArea(
                    player as ServerPlayerEntity,
                    world as ServerWorld,
                    8
                )
                return@register ActionResult.CONSUME
            }
            return@register ActionResult.PASS
        }
        // when right-clicking a block, ledger the block in front of it
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (isLedgerBook(player.getStackInHand(hand))) {
                ledgerBlock(
                    player as ServerPlayerEntity,
                    world as ServerWorld,
                    hitResult.blockPos.offset(hitResult.side)
                )
                return@register ActionResult.CONSUME
            }
            return@register ActionResult.PASS
        }
        // when left-clicking a block, ledger the block that was clicked
        AttackBlockCallback.EVENT.register { player, world, hand, pos, _ ->
            if (isLedgerBook(player.getStackInHand(hand))) {
                ledgerBlock(
                    player as ServerPlayerEntity,
                    world as ServerWorld,
                    pos
                )
                return@register ActionResult.CONSUME
            }
            return@register ActionResult.PASS
        }
    }

    fun isLedgerBook(itemStack: ItemStack): Boolean {
        val data = itemStack.components.get(DataComponentTypes.CUSTOM_DATA)
        return data?.contains("ledger") ?: false
    }

    fun ledgerArea(player: ServerPlayerEntity, world: ServerWorld, radius: Int = 8) {
        // Define parameters for the ledger search
        val params = ActionSearchParams.build {
            this.worlds = mutableSetOf(Negatable.allow(world.registryKey.value))
            this.bounds = BlockBox.create(
                player.blockPos.add(-radius, -radius, -radius),
                player.blockPos.add(radius, radius, radius)
            )
            this.actions = mutableSetOf(
                Negatable.allow("item-pick-up"),
                Negatable.allow("item-drop"),
                Negatable.allow("entity-kill"),
                Negatable.allow("entity-change"),
                Negatable.allow("entity-mount"),
                Negatable.allow("entity-dismount"),
            )
        }
        ledger(player, player.getCommandSource(world as ServerWorld?), params)
    }

    fun ledgerBlock(player: ServerPlayerEntity, world: ServerWorld, blockPos: BlockPos) {
        // Define parameters for the ledger search
        val params = ActionSearchParams.build {
            this.worlds = mutableSetOf(Negatable.allow(world.registryKey.value))
            this.bounds = BlockBox.create(blockPos, blockPos)
        }
        ledger(player, player.getCommandSource(world as ServerWorld?), params)
    }

    fun ledger(player: ServerPlayerEntity, commandSource: ServerCommandSource, params: ActionSearchParams) {
        Ledger.launch {
            // Get the actions for the selected block
            val actions = getActions(params, 2)
            // Map the retrieved actions to text objects
            val actionTexts =
                if (actions.isEmpty()) listOf(Text.translatable("error.ledger.command.no_results"))
                else actions.map { it.getText(commandSource) }
            // Paginate the lines
            val pages = paginateLines(actionTexts, 4)
            // Create a book from the pages
            val book = createBook(pages)
            // Open the book to the player
            openBook(player, book)
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
        val text = Text.empty()
        text.append(this.getTimeIcon())
        text.append(" ")
        text.append(Text.translatable("text.ledger.action.${aat.identifier}"))
        text.append(" ")
        text.append(aat.getObjectMessage(source))
        text.append(Text.literal("\n> ").setStyle(Style.EMPTY.withColor(Colors.LIGHT_GRAY)))
        text.append(aat.getSourceMessage().copyContentOnly().setStyle(Style.EMPTY.withColor(Colors.LIGHT_GRAY).withItalic(true)))

        if (aat.rolledBack) {
            text.formatted(Formatting.STRIKETHROUGH)
        }

        return text
    }

    @OptIn(ExperimentalTime::class)
    fun AbstractActionType.getTimeIcon(): MutableText {
        return Text.literal("\uD83D\uDD50").setStyle(this.getTimeMessage().style)
    }

    fun paginateLines(actionTexts: List<MutableText>, actionsPerPage: Int): List<RawFilteredPair<Text>> {
        var page = Text.empty()
        val pages = mutableListOf<RawFilteredPair<Text>>()
        for (i in actionTexts.indices) {
            val text = actionTexts[i]
            text.style =
                if (i % 2 == 0) TextColorPallet.primary
                else TextColorPallet.secondary
            page = page.append(text)
            page = page.append("\n\n")
            if ((i % actionsPerPage) == (actionsPerPage - 1) || i == (actionTexts.size - 1)) {
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
