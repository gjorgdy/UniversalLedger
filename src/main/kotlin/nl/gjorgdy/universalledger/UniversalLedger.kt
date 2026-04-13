package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.AbstractActionType
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.api.ExtensionManager
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.Negatable
import com.github.quiltservertools.ledger.utility.TextColorPallet
import jdk.internal.org.jline.utils.Colors
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockBox
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.NbtContents
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.WrittenBookContent
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.levelgen.structure.BoundingBox
import nl.gjorgdy.universalledger.config.BookConfig
import java.util.Properties
import javax.xml.crypto.Data
import kotlin.time.ExperimentalTime

class UniversalLedger : ModInitializer {

    @OptIn(ExperimentalTime::class)
    override fun onInitialize() {
        Ledger.launch {
            ExtensionManager.registerExtension(UniversalLedgerExtension)
        }
        // When right-clicking in the air, ledger events in an 8-block radius
        UseItemCallback.EVENT.register { player, world, hand ->
            if (isLedgerBook(player.getItemInHand(hand))) {
                ledgerArea(
                    player as ServerPlayer, world as ServerLevel, 8
                )
                return@register InteractionResult.CONSUME
            }
            return@register InteractionResult.PASS
        }
        // When right-clicking a block-entity, ledger this block-entity's inventory
        // if not a block-entity, ledger the area
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (isLedgerBook(player.getItemInHand(hand))) {
                if (world.getBlockEntity(hitResult.blockPos) != null) {
                    ledgerInventory(
                        player as ServerPlayer, world as ServerLevel, hitResult.blockPos
                    )
                }
                return@register InteractionResult.CONSUME
            }
            return@register InteractionResult.PASS
        }
        // when left-clicking a block, ledger the block that was clicked
        AttackBlockCallback.EVENT.register { player, world, hand, pos, _ ->
            if (isLedgerBook(player.getItemInHand(hand))) {
                ledgerBlock(
                    player as ServerPlayer, world as ServerLevel, pos
                )
                return@register InteractionResult.CONSUME
            }
            return@register InteractionResult.PASS
        }
    }

    fun isLedgerBook(itemStack: ItemStack): Boolean {
        val nbt: CustomData? = itemStack.components.get(DataComponents.CUSTOM_DATA)
        return nbt?.copyTag()?.contains("ledger") ?: false
    }

    fun ledgerArea(player: ServerPlayer, world: ServerLevel, radius: Int = 8) {
        // Define parameters for the ledger search
        val params = ActionSearchParams.build {
            this.worlds = mutableSetOf(Negatable.allow(world.dimension().identifier()))
            this.bounds = BoundingBox.fromCorners(
                player.onPos.offset(-radius, -radius, -radius), player.onPos.offset(radius, radius, radius)
            )
            this.actions = BookConfig.getInstance().areaActions
        }
        ledger(player, player.createCommandSourceStack(), params)
    }

    fun ledgerInventory(player: ServerPlayer, world: ServerLevel, blockPos: BlockPos) {
        // check if double chest, and expend bounds if so
        var blockPosB = blockPos
        val block = world.getBlockState(blockPos)
        if (block.`is`(Blocks.CHEST)) {
            val type = block.getValue(ChestBlock.TYPE)
            val direction = block.getValue(ChestBlock.FACING)
            if (type == ChestType.LEFT) {
                val rotated = direction.clockWise
                blockPosB = blockPos.relative(rotated)
            } else if (type == ChestType.RIGHT) {
                val rotated = direction.counterClockWise
                blockPosB = blockPos.relative(rotated)
            }
        }
        // Define parameters for the ledger search
        val params = ActionSearchParams.build {
            this.worlds = mutableSetOf(Negatable.allow(world.dimension().identifier()))
            this.bounds = BoundingBox.fromCorners(blockPos, blockPosB)
            this.actions = BookConfig.getInstance().inventoryActions
        }
        ledger(player, player.createCommandSourceStack(), params)
    }

    fun ledgerBlock(player: ServerPlayer, world: ServerLevel, blockPos: BlockPos) {
        // Define parameters for the ledger search
        val params = ActionSearchParams.build {
            this.worlds = mutableSetOf(Negatable.allow(world.dimension().identifier()))
            this.bounds = BoundingBox.fromCorners(blockPos, blockPos)
            this.actions = BookConfig.getInstance().inventoryActions
        }
        ledger(player, player.createCommandSourceStack(), params)
    }

    fun ledgerChat(player: ServerPlayerEntity, commandSource: ServerCommandSource, params: ActionSearchParams) {
        Ledger.launch {
            for (i in 1..2) {
                val actions: List<ActionType> = DatabaseManager.searchActions(params, i).actions
                if (actions.isEmpty() && i > 1) break
                if (actions.isEmpty()) {
                    player.sendMessage(
                        Text.translatable("error.ledger.command.no_results")
                    )
                } else {
                    actions.forEach { action ->
                        player.sendMessage(action.getText(commandSource, true))
                    }
                }
            }
        }
    }

    fun ledger(player: ServerPlayer, commandSource: CommandSourceStack, params: ActionSearchParams) {
        if (BookConfig.getInstance().chatOnly) {
            ledgerChat(player, commandSource, params)
            return
        }
        Ledger.launch {
            openBook(player, createEmptyBook())
            val pages: MutableList<Filterable<Component>> = mutableListOf()
            // Get the actions for the selected block
            for (i in 1..2) {
                val actions: List<ActionType> = DatabaseManager.searchActions(params, i).actions
                if (actions.isEmpty() && i > 1) break
                // Map the retrieved actions to text objects
                val actionTexts = if (actions.isEmpty()) listOf(Component.translatable("error.ledger.command.no_results"))
                else actions.map { it.getText(commandSource) }
                // Paginate the lines
                pages.addAll(paginateLines(actionTexts, 4))
            }
            // Create a book from the pages
            val book = createBook(pages)
            // Open the book to the player
            openBook(player, book)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun ActionType.getText(source: CommandSourceStack, printTime: Boolean = false): MutableComponent {
        val aat = this as AbstractActionType
        val text = Component.empty()
        text.append(if (printTime) this.getTimeMessage() else this.getTimeIcon())
        text.append(" ")
        text.append(
            aat.getSourceMessage().plainCopy().setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true))
        )
        text.append(" ")
        text.append(Component.translatable("text.ledger.action.${aat.identifier}"))
        text.append(" ")
        text.append(aat.getObjectMessage(source))
        if (aat.rolledBack) text.withStyle(ChatFormatting.STRIKETHROUGH)
        return text
    }

    @OptIn(ExperimentalTime::class)
    fun AbstractActionType.getTimeIcon(): MutableComponent {
        return Component.literal("\uD83D\uDD50").setStyle(this.getTimeMessage().style)
    }

    fun paginateLines(actionTexts: List<MutableComponent>, actionsPerPage: Int): List<Filterable<Component>> {
        var page = Component.empty()
        val pages = mutableListOf<Filterable<Component>>()
        for (i in actionTexts.indices) {
            val text = actionTexts[i]
            text.style = if (i % 2 == 0) TextColorPallet.primary
            else TextColorPallet.secondary
            page = page.append(text)
            page = page.append("\n\n")
            if ((i % actionsPerPage) == (actionsPerPage - 1) || i == (actionTexts.size - 1)) {
                pages.add(Filterable.passThrough(page))
                page = Component.empty()
            }
        }
        return pages
    }

    fun createBook(pages: List<Filterable<Component>>): ItemStack {
        val bookComponent = WrittenBookContent(
            Filterable.passThrough("Ledger"), "", 0, pages, true
        )
        val book = Items.WRITTEN_BOOK.defaultInstance
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, bookComponent)
        return book
    }

    fun createEmptyBook(): ItemStack {
        val loadingText = Component.literal("Loading logs...").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
        val bookComponent = WrittenBookContent(
            Filterable.passThrough("Ledger"), "", 0, listOf(Filterable.passThrough(loadingText)), true
        )
        val book = Items.WRITTEN_BOOK.defaultInstance
        book[DataComponents.WRITTEN_BOOK_CONTENT] = bookComponent
        return book
    }

    fun openBook(player: ServerPlayer, book: ItemStack) {
        player.connection.send(ClientboundSetPlayerInventoryPacket(40, book))
        player.openItemGui(book, InteractionHand.OFF_HAND)
        player.connection.send(ClientboundSetPlayerInventoryPacket(40, player.inventory.getItem(40)))
    }

}
