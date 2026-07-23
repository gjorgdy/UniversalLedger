package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.actions.AbstractActionType
import com.github.quiltservertools.ledger.actions.ActionType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import kotlin.time.ExperimentalTime

class ActionTypeExtension {
    companion object {
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
    }
}