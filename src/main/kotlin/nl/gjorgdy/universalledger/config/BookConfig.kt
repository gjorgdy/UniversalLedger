package nl.gjorgdy.universalledger.config

import com.github.quiltservertools.ledger.config.config
import com.github.quiltservertools.ledger.utility.Negatable

class BookConfig {

    val areaActions: MutableSet<Negatable<String>>?
    val inventoryActions: MutableSet<Negatable<String>>?
    val blockActions: MutableSet<Negatable<String>>?

    private constructor() {
        areaActions = config[BookSpec.areaActions].map { Negatable.allow(it) }.toMutableSet()
        inventoryActions = config[BookSpec.inventoryActions].map { Negatable.allow(it) }.toMutableSet()
        blockActions = config[BookSpec.blockActions].map { Negatable.allow(it) }.toMutableSet()
    }

    companion object {

        @Volatile
        private var instance: BookConfig? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: BookConfig().also { instance = it }
            }
    }

}