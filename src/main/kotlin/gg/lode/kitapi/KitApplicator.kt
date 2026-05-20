package gg.lode.kitapi

import org.bukkit.entity.Player
import org.bukkit.inventory.PlayerInventory

/**
 * Applies a [Kit] to a target. Generic so PhytorPractice (target = Player) and
 * Catalyst (target = a bot wrapper) can each provide their own implementation
 * without leaking framework-specific types into this API.
 */
fun interface KitApplicator<T> {
    /** Copy the kit's items into the target's inventory. */
    fun apply(target: T, kit: Kit)
}

/**
 * Stock applicator for vanilla Players. Suitable for PhytorPractice duels.
 * Clears the target's inventory first so leftover items can't leak across kits.
 */
object PlayerKitApplicator : KitApplicator<Player> {
    override fun apply(target: Player, kit: Kit) {
        val inv: PlayerInventory = target.inventory
        inv.clear()
        inv.helmet = kit.armor[0]?.clone()
        inv.chestplate = kit.armor[1]?.clone()
        inv.leggings = kit.armor[2]?.clone()
        inv.boots = kit.armor[3]?.clone()
        inv.setItemInOffHand(kit.offhand?.clone())
        for (i in 0 until Kit.INV_SLOTS) {
            kit.contents[i]?.let { inv.setItem(i, it.clone()) }
        }
        target.updateInventory()
    }
}
