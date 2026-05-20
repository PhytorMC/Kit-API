package gg.lode.kitapi

import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * A kit is a complete inventory loadout — armor + offhand + 36 main inventory slots.
 *
 * Built deliberately to be storage-agnostic and target-agnostic:
 *   - [KitRepository] persists Kits to whatever backend (Mongo, file, …)
 *   - [KitApplicator] copies a Kit onto whatever target (Player, BotInventory, …)
 *
 * The slot indices match Bukkit's PlayerInventory layout so applicators can
 * push items directly without remapping.
 */
data class Kit(
    /** Unique kit name. Case-preserving but lookup is typically case-insensitive at the repo layer. */
    val name: String,
    /** Helmet, chestplate, leggings, boots — index 0..3 (or null). */
    val armor: Array<ItemStack?>,
    /** Off-hand item, or null. */
    val offhand: ItemStack?,
    /** 36 main-inventory slots, indices 0..35 (0..8 = hotbar). Each may be null. */
    val contents: Array<ItemStack?>,
    /** Who created/owns this kit. Server-wide kits use null. */
    val ownerUuid: UUID? = null,
    /** Optional category — e.g. "sword", "axe", "uhc" — for filtering in lists. */
    val category: String? = null,
    /** Optional human description. */
    val description: String? = null,
    /** Unix millis. */
    val createdAt: Long = System.currentTimeMillis(),
    /** Unix millis. Bumped on every save. */
    val updatedAt: Long = createdAt
) {
    init {
        require(armor.size == ARMOR_SLOTS) { "armor must have exactly $ARMOR_SLOTS slots, got ${armor.size}" }
        require(contents.size == INV_SLOTS) { "contents must have exactly $INV_SLOTS slots, got ${contents.size}" }
    }

    /** Free-standing copy for safe mutation downstream. */
    fun clone(): Kit = copy(
        armor = armor.map { it?.clone() }.toTypedArray(),
        offhand = offhand?.clone(),
        contents = contents.map { it?.clone() }.toTypedArray()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Kit) return false
        return name == other.name && createdAt == other.createdAt && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int = name.hashCode() * 31 + updatedAt.hashCode()

    companion object {
        const val ARMOR_SLOTS = 4
        const val INV_SLOTS = 36

        /** Convenient empty kit for an editor to start populating. */
        fun blank(name: String, ownerUuid: UUID? = null): Kit = Kit(
            name = name,
            armor = arrayOfNulls(ARMOR_SLOTS),
            offhand = null,
            contents = arrayOfNulls(INV_SLOTS),
            ownerUuid = ownerUuid
        )
    }
}
