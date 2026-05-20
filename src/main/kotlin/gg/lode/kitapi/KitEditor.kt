package gg.lode.kitapi

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Chest-GUI kit editor.
 *
 *   row 0 (slots  0..8) — hotbar       (Kit.contents 0..8)
 *   row 1 (slots  9..17) — inv row     (Kit.contents 9..17)
 *   row 2 (slots 18..26) — inv row     (Kit.contents 18..26)
 *   row 3 (slots 27..35) — inv row     (Kit.contents 27..35)
 *   row 4 — armor (36..39) + filler + offhand (41) + filler
 *   row 5 — buttons: 49 SAVE, 50 CLEAR, 51 COPY_FROM_ME, rest filler
 *
 * Open one with [open]. Register [KitEditor.Listener] once at plugin enable.
 * Clicking SAVE persists to the [KitRepository] and closes; closing without
 * clicking SAVE discards the in-progress edits.
 */
class KitEditor(
    private val repository: KitRepository,
    private val plugin: Plugin
) {
    /** Open the editor for [player] on a Kit. Existing kit pre-populated; new = blank. */
    fun open(player: Player, kitName: String, category: String? = null) {
        val existing = repository.findByName(kitName)
        val kit = existing ?: Kit.blank(kitName, ownerUuid = player.uniqueId).copy(category = category)
        val holder = Holder(this, kit.name, kit.category ?: category)
        val inv = Bukkit.createInventory(holder, INV_SIZE, EDITOR_TITLE)
        holder.heldInventory = inv
        layoutBaseChrome(inv)
        applyKitToInventory(inv, kit)
        active[player.uniqueId] = holder
        player.openInventory(inv)
    }

    private fun applyKitToInventory(inv: Inventory, kit: Kit) {
        for (i in 0 until Kit.INV_SLOTS) {
            inv.setItem(i, kit.contents[i]?.clone())
        }
        for (i in 0 until Kit.ARMOR_SLOTS) {
            inv.setItem(SLOT_ARMOR_HELMET + i, kit.armor[i]?.clone())
        }
        inv.setItem(SLOT_OFFHAND, kit.offhand?.clone())
    }

    /** Read current GUI state back into a Kit instance. */
    private fun readKitFromInventory(holder: Holder): Kit {
        val inv = holder.heldInventory ?: error("editor inventory missing")
        val contents = Array<ItemStack?>(Kit.INV_SLOTS) { i -> inv.getItem(i)?.clone() }
        val armor = Array<ItemStack?>(Kit.ARMOR_SLOTS) { i -> inv.getItem(SLOT_ARMOR_HELMET + i)?.clone() }
        val offhand = inv.getItem(SLOT_OFFHAND)?.clone()
        val now = System.currentTimeMillis()
        val existing = repository.findByName(holder.kitName)
        return existing?.copy(
            armor = armor,
            offhand = offhand,
            contents = contents,
            updatedAt = now,
            category = holder.category ?: existing.category
        ) ?: Kit(
            name = holder.kitName,
            armor = armor,
            offhand = offhand,
            contents = contents,
            category = holder.category,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun layoutBaseChrome(inv: Inventory) {
        // Mark armor slots so the player sees what each placeholder is for.
        inv.setItem(SLOT_ARMOR_HELMET, placeholder(Material.IRON_HELMET, "<gray>Helmet slot"))
        inv.setItem(SLOT_ARMOR_CHEST, placeholder(Material.IRON_CHESTPLATE, "<gray>Chestplate slot"))
        inv.setItem(SLOT_ARMOR_LEGS, placeholder(Material.IRON_LEGGINGS, "<gray>Leggings slot"))
        inv.setItem(SLOT_ARMOR_BOOTS, placeholder(Material.IRON_BOOTS, "<gray>Boots slot"))
        inv.setItem(SLOT_OFFHAND, placeholder(Material.SHIELD, "<gray>Off-hand slot"))

        val filler = filler()
        for (slot in intArrayOf(40, 42, 43, 44, 45, 46, 47, 48, 52, 53)) {
            inv.setItem(slot, filler)
        }
        inv.setItem(SLOT_SAVE, button(Material.EMERALD, "<green><b>SAVE", "<gray>Persist this kit."))
        inv.setItem(SLOT_CLEAR, button(Material.BARRIER, "<red><b>CLEAR", "<gray>Empty every slot."))
        inv.setItem(SLOT_COPY_ME, button(Material.CHEST, "<aqua><b>COPY MY INVENTORY",
            "<gray>Replace editor contents with whatever you're holding."))
    }

    // ---- internals ----------------------------------------------------------

    private val active: MutableMap<UUID, Holder> = mutableMapOf()
    private val savedThisSession: MutableSet<UUID> = mutableSetOf()

    private class Holder(
        val editor: KitEditor,
        val kitName: String,
        val category: String?,
    ) : InventoryHolder {
        var heldInventory: Inventory? = null
        override fun getInventory(): Inventory = heldInventory ?: error("inventory not set")
    }

    private fun isPlaceholder(stack: ItemStack?): Boolean =
        stack?.itemMeta?.persistentDataContainer
            ?.has(NamespacedKeys.placeholder(plugin))
            ?: false

    private fun isControl(slot: Int): Boolean =
        slot in setOf(SLOT_SAVE, SLOT_CLEAR, SLOT_COPY_ME, 40, 42, 43, 44, 45, 46, 47, 48, 52, 53)

    /** Public listener — register once per host plugin. */
    class Listener(private val editor: KitEditor) : org.bukkit.event.Listener {

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        fun onClick(event: InventoryClickEvent) {
            val holder = event.inventory.holder as? Holder ?: return
            if (holder.editor !== editor) return
            val player = event.whoClicked as? Player ?: return
            val slot = event.rawSlot

            // Top inventory? slot < INV_SIZE. Bottom? rawSlot >= INV_SIZE.
            if (slot >= INV_SIZE) return  // let normal interactions with player inv proceed

            // Button row 5
            when (slot) {
                SLOT_SAVE -> {
                    event.isCancelled = true
                    editor.handleSave(player, holder)
                }
                SLOT_CLEAR -> {
                    event.isCancelled = true
                    editor.handleClear(holder.heldInventory!!)
                }
                SLOT_COPY_ME -> {
                    event.isCancelled = true
                    editor.handleCopyFrom(player, holder.heldInventory!!)
                }
                else -> {
                    // Allow item movement in non-button slots (armor, offhand, contents).
                    // The chrome placeholders are auto-replaced when player drops a real item.
                    if (editor.isControl(slot)) {
                        event.isCancelled = true
                    } else if (editor.isPlaceholder(event.currentItem)) {
                        // Clearing a placeholder = empty the slot, no item duplication.
                        event.currentItem = null
                    }
                }
            }
        }

        @EventHandler
        fun onDrag(event: InventoryDragEvent) {
            val holder = event.inventory.holder as? Holder ?: return
            if (holder.editor !== editor) return
            // Block drags that touch a control slot.
            for (slot in event.rawSlots) {
                if (slot < INV_SIZE && editor.isControl(slot)) {
                    event.isCancelled = true
                    return
                }
            }
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            val holder = event.inventory.holder as? Holder ?: return
            if (holder.editor !== editor) return
            val playerId = event.player.uniqueId
            editor.active.remove(playerId)
            editor.savedThisSession.remove(playerId)
        }
    }

    private fun handleSave(player: Player, holder: Holder) {
        val kit = readKitFromInventory(holder)
        // Strip any leftover placeholders.
        val cleanedArmor = kit.armor.map { if (isPlaceholder(it)) null else it }.toTypedArray()
        val cleanedContents = kit.contents.map { if (isPlaceholder(it)) null else it }.toTypedArray()
        val cleanedOffhand = if (isPlaceholder(kit.offhand)) null else kit.offhand
        val cleaned = kit.copy(armor = cleanedArmor, contents = cleanedContents, offhand = cleanedOffhand)

        try {
            repository.save(cleaned)
            savedThisSession.add(player.uniqueId)
            player.sendMessage(Component.text("Saved kit '${cleaned.name}'.", NamedTextColor.GREEN))
            player.closeInventory()
        } catch (e: Exception) {
            player.sendMessage(Component.text("Save failed: ${e.message}", NamedTextColor.RED))
        }
    }

    private fun handleClear(inv: Inventory) {
        for (i in 0 until Kit.INV_SLOTS) inv.setItem(i, null)
        for (i in 0 until Kit.ARMOR_SLOTS) inv.setItem(SLOT_ARMOR_HELMET + i, null)
        inv.setItem(SLOT_OFFHAND, null)
        // Re-add chrome placeholders so the user sees the slot purpose again.
        layoutBaseChrome(inv)
    }

    private fun handleCopyFrom(player: Player, inv: Inventory) {
        val src = player.inventory
        for (i in 0 until Kit.INV_SLOTS) inv.setItem(i, src.getItem(i)?.clone())
        inv.setItem(SLOT_ARMOR_HELMET, src.helmet?.clone() ?: placeholder(Material.IRON_HELMET, "<gray>Helmet slot"))
        inv.setItem(SLOT_ARMOR_CHEST, src.chestplate?.clone() ?: placeholder(Material.IRON_CHESTPLATE, "<gray>Chestplate slot"))
        inv.setItem(SLOT_ARMOR_LEGS, src.leggings?.clone() ?: placeholder(Material.IRON_LEGGINGS, "<gray>Leggings slot"))
        inv.setItem(SLOT_ARMOR_BOOTS, src.boots?.clone() ?: placeholder(Material.IRON_BOOTS, "<gray>Boots slot"))
        inv.setItem(SLOT_OFFHAND, src.itemInOffHand.takeUnless { it.type == Material.AIR }?.clone()
            ?: placeholder(Material.SHIELD, "<gray>Off-hand slot"))
    }

    // ---- helpers ------------------------------------------------------------

    private fun placeholder(material: Material, label: String): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(MiniMessage.deserialize(label)
            .decoration(TextDecoration.ITALIC, false))
        meta.persistentDataContainer.set(NamespacedKeys.placeholder(plugin),
            org.bukkit.persistence.PersistentDataType.BYTE, 1)
        stack.itemMeta = meta
        return stack
    }

    private fun button(material: Material, label: String, lore: String): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(MiniMessage.deserialize(label).decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(MiniMessage.deserialize(lore).decoration(TextDecoration.ITALIC, false)))
        meta.persistentDataContainer.set(NamespacedKeys.placeholder(plugin),
            org.bukkit.persistence.PersistentDataType.BYTE, 1)
        stack.itemMeta = meta
        return stack
    }

    private fun filler(): ItemStack {
        val stack = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = stack.itemMeta
        meta.displayName(Component.text(" "))
        meta.persistentDataContainer.set(NamespacedKeys.placeholder(plugin),
            org.bukkit.persistence.PersistentDataType.BYTE, 1)
        stack.itemMeta = meta
        return stack
    }

    private object NamespacedKeys {
        fun placeholder(plugin: Plugin) =
            org.bukkit.NamespacedKey(plugin, "kit_editor_placeholder")
    }

    private object MiniMessage {
        private val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        fun deserialize(s: String): Component = mm.deserialize(s)
    }

    companion object {
        const val INV_SIZE = 54
        val EDITOR_TITLE: Component = Component.text("Kit Editor", NamedTextColor.DARK_GRAY)

        // Row 4 — armor + offhand
        const val SLOT_ARMOR_HELMET = 36
        const val SLOT_ARMOR_CHEST = 37
        const val SLOT_ARMOR_LEGS = 38
        const val SLOT_ARMOR_BOOTS = 39
        const val SLOT_OFFHAND = 41

        // Row 5 — buttons
        const val SLOT_SAVE = 49
        const val SLOT_CLEAR = 50
        const val SLOT_COPY_ME = 51
    }
}
