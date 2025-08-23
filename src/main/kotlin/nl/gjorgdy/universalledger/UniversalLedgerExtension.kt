package nl.gjorgdy.universalledger

import com.github.quiltservertools.ledger.api.LedgerExtension
import com.uchuhimo.konf.ConfigSpec
import net.minecraft.util.Identifier
import nl.gjorgdy.universalledger.config.BookSpec

object UniversalLedgerExtension : LedgerExtension {
    override fun getIdentifier(): Identifier {
        return Identifier.of("universalledger", "universal_ledger")
    }

    override fun getConfigSpecs(): List<ConfigSpec> {
        return listOf(BookSpec)
    }

}