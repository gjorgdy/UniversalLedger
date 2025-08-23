Universal Ledger is an extension for [Ledger](https://modrinth.com/mod/ledger) to make logs available for all players.

Intended for smaller servers to take some work away from administrators.

## How to use
To start off, you need to craft the Ledger Book

![Crafting recipe](https://cdn.modrinth.com/data/cached_images/d5cf29e679aa99f53d125bfc6c681fa7bd84b150.png)

### To check an inventory 
(chest, furnace, shulkers etc.)

_right-click the inventory block using the book_

![Log of a chest](https://cdn.modrinth.com/data/cached_images/ad18d7360c27137b50eb6b3bee01f638ce304c0b.png)

### To check entities
_right-click in the air or any non-inventory block using the book_

![Log of entities](https://cdn.modrinth.com/data/cached_images/c4cb32106596725c3b4e0f623d973f95d51a26e5.png)

### To check blocks
_left-click the block using the book_

![Logs of a block](https://cdn.modrinth.com/data/cached_images/15cadd5f939b74b122c634f01af7e729b0ad1bb8.png)

### Config
_you are able to configure which triggers show which actions in the book_

To change the default configuration, paste the following at the end of the ``/config/ledger.toml`` file:

```toml
[book]
# This section relates to the Universal Ledger extension
# Here you can specify what players are able to see when using a Ledger Book

# What actions should be shown when right-clicking anywhere but an inventory block
areaActions = ["item-pick-up", "item-drop", "entity-kill", "entity-change", "entity-mount", "entity-dismount"]
# What actions should be shown when right-clicking an inventory block
inventoryActions = ["item-insert", "item-remove"]
# What actions should be shown when left-clicking a block
blockActions = ["block-place", "block-break", "block-change"]
```
