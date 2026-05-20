package gg.lode.kitapi

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.UpdateOptions
import org.bson.Document
import org.bson.types.Binary
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * MongoDB-backed [KitRepository]. Stores ItemStack via Bukkit's
 * `serializeAsBytes` so the persisted form survives server upgrades that
 * change NMS layout.
 *
 * The host plugin is responsible for picking the [MongoDatabase] — that's how
 * the kit collection lands in `network` (Vertex's shared DB) or whatever else
 * the deployer prefers.
 */
class MongoKitRepository(
    database: MongoDatabase,
    collectionName: String = "kits",
    private val logger: Logger = Logger.getLogger("Kit-API")
) : KitRepository {

    private val collection: MongoCollection<Document> = database.getCollection(collectionName)

    init {
        // case-insensitive name uniqueness via a denormalized lowercased field
        collection.createIndex(Indexes.ascending("name_lower"), IndexOptions().unique(true))
        collection.createIndex(Indexes.ascending("category"))
        collection.createIndex(Indexes.descending("updated_at"))
    }

    override fun save(kit: Kit): Kit {
        val now = System.currentTimeMillis()
        val updated = kit.copy(updatedAt = now)
        val doc = toDoc(updated)
        try {
            collection.updateOne(
                Filters.eq("name_lower", kit.name.lowercase()),
                Document("\$set", doc),
                UpdateOptions().upsert(true)
            )
        } catch (e: Exception) {
            logger.log(Level.WARNING, "[Kit] save '${kit.name}' failed: ${e.message}", e)
            throw e
        }
        return updated
    }

    override fun findByName(name: String): Kit? {
        val doc = collection.find(Filters.eq("name_lower", name.lowercase())).first() ?: return null
        return fromDoc(doc)
    }

    override fun listAll(): List<Kit> {
        return collection.find().sort(Document("updated_at", -1))
            .mapNotNull { runCatching { fromDoc(it) }.getOrNull() }
            .toList()
    }

    override fun listByCategory(category: String?): List<Kit> {
        val filter = if (category == null) Filters.eq("category", null) else Filters.eq("category", category)
        return collection.find(filter).sort(Document("updated_at", -1))
            .mapNotNull { runCatching { fromDoc(it) }.getOrNull() }
            .toList()
    }

    override fun delete(name: String): Boolean {
        val result = collection.deleteOne(Filters.eq("name_lower", name.lowercase()))
        return result.deletedCount > 0
    }

    override fun namesStartingWith(prefix: String, limit: Int): List<String> {
        val regex = "^" + java.util.regex.Pattern.quote(prefix.lowercase())
        return collection.find(Filters.regex("name_lower", regex))
            .limit(limit)
            .mapNotNull { it.getString("name") }
            .toList()
    }

    // ---- (de)serialization --------------------------------------------------

    private fun toDoc(kit: Kit): Document {
        val doc = Document()
        doc["name"] = kit.name
        doc["name_lower"] = kit.name.lowercase()
        doc["owner_uuid"] = kit.ownerUuid?.toString()
        doc["category"] = kit.category
        doc["description"] = kit.description
        doc["created_at"] = kit.createdAt
        doc["updated_at"] = kit.updatedAt
        doc["armor"] = kit.armor.map { it?.let { stack -> Binary(stack.serializeAsBytes()) } }
        doc["offhand"] = kit.offhand?.let { Binary(it.serializeAsBytes()) }
        doc["contents"] = kit.contents.map { it?.let { stack -> Binary(stack.serializeAsBytes()) } }
        return doc
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromDoc(doc: Document): Kit {
        val name = doc.getString("name") ?: error("kit doc missing name")
        val armorList = (doc.get("armor") as? List<Binary?>).orEmpty()
        val contentsList = (doc.get("contents") as? List<Binary?>).orEmpty()

        val armor = Array<ItemStack?>(Kit.ARMOR_SLOTS) { i ->
            armorList.getOrNull(i)?.let { ItemStack.deserializeBytes(it.data) }
        }
        val contents = Array<ItemStack?>(Kit.INV_SLOTS) { i ->
            contentsList.getOrNull(i)?.let { ItemStack.deserializeBytes(it.data) }
        }
        val offhand = (doc.get("offhand") as? Binary)?.let { ItemStack.deserializeBytes(it.data) }

        return Kit(
            name = name,
            armor = armor,
            offhand = offhand,
            contents = contents,
            ownerUuid = doc.getString("owner_uuid")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            category = doc.getString("category"),
            description = doc.getString("description"),
            createdAt = doc.getLong("created_at") ?: 0L,
            updatedAt = doc.getLong("updated_at") ?: 0L
        )
    }
}
