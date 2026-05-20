package gg.lode.kitapi

/**
 * Storage-agnostic kit repository. One source of truth for Kit CRUD.
 *
 * Implementations are responsible for picking a case-sensitivity convention
 * for [findByName]; the bundled [MongoKitRepository] does case-insensitive
 * lookups by storing a lowercased `name_lower` column.
 */
interface KitRepository {
    /** Persist a kit (insert or update by name). Returns the saved Kit (updatedAt bumped). */
    fun save(kit: Kit): Kit

    /** Look up a kit by name. Case-insensitive in stock impls. Null if missing. */
    fun findByName(name: String): Kit?

    /** All kits, newest first. Use [listByCategory] for filtered views. */
    fun listAll(): List<Kit>

    /** Kits in a specific category (sword / axe / uhc / …). Null returns ALL kits without a category. */
    fun listByCategory(category: String?): List<Kit>

    /** Delete by name. Returns true if a kit was actually removed. */
    fun delete(name: String): Boolean

    /** Kit names matching a prefix — for tab complete. */
    fun namesStartingWith(prefix: String, limit: Int = 32): List<String>
}
