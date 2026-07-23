package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.utility.TextColorPallet
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

class BookUtils {
    companion object {
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

        fun openBook(player: ServerPlayer, book: ItemStack = createEmptyBook()) {
            player.connection.send(ClientboundSetPlayerInventoryPacket(player.inventory.selectedSlot, book))
            player.openItemGui(book, InteractionHand.MAIN_HAND)
            player.connection.send(ClientboundSetPlayerInventoryPacket(player.inventory.selectedSlot, player.inventory.selectedItem))
//        player.connection.send(ClientboundSetPlayerInventoryPacket(40, player.inventory.getItem(40)))
        }
    }
}