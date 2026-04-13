package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.api.LedgerExtension
import com.uchuhimo.konf.ConfigSpec
import net.minecraft.resources.Identifier
import nl.gjorgdy.universalledger.config.BookSpec

object UniversalLedgerExtension : LedgerExtension {
    override fun getIdentifier(): Identifier {
        return Identifier.fromNamespaceAndPath("universalledger", "universal_ledger")
    }

    override fun getConfigSpecs(): List<ConfigSpec> {
        return listOf(BookSpec)
    }

}