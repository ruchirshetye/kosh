package com.amitjha.kosh.data

/**
 * Parent → child view of the categories table. Subcategories are one level deep: a category whose [CategoryEntity.parentKey]
 * names a category that doesn't exist (or is itself a subcategory) is treated as top-level, so nothing gets lost.
 */
class CategoryTree(categories: Collection<CategoryEntity>) {
    private val byKey: Map<String, CategoryEntity> = categories.associateBy { it.key }

    /** Children of each parent key, in name order. */
    private val children: Map<String, List<CategoryEntity>> = categories
        .filter { c -> c.parentKey != null && byKey[c.parentKey]?.parentKey == null && c.parentKey != c.key && byKey.containsKey(c.parentKey) }
        .groupBy { it.parentKey!! }
        .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }

    private val parentOf: Map<String, String> = children.flatMap { (p, list) -> list.map { it.key to p } }.toMap()

    operator fun get(key: String): CategoryEntity? = byKey[key]

    /** The top-level category [key] rolls up into ([key] itself when it is top-level or unknown). */
    fun rootOf(key: String): String = parentOf[key] ?: key

    fun isSub(key: String): Boolean = key in parentOf

    fun childrenOf(key: String): List<CategoryEntity> = children[key].orEmpty()

    /** [key] plus its subcategories: what a filter or budget on [key] covers. */
    fun keysUnder(key: String): Set<String> = setOf(key) + childrenOf(key).map { it.key }

    /** Top-level categories (including orphans), in the table's order. */
    fun roots(all: List<CategoryEntity>): List<CategoryEntity> = all.filter { !isSub(it.key) }

    /** Totals per category folded into their top-level category. */
    fun <N : Number> rollUp(totals: Map<String, N>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        totals.forEach { (k, v) -> val r = rootOf(k); out[r] = (out[r] ?: 0L) + v.toLong() }
        return out
    }

    /** "Bills & Utilities › Electricity" for a subcategory, else the plain name; [fallback] when unknown. */
    fun label(key: String, fallback: String = key): String {
        val c = byKey[key] ?: return fallback
        val p = parentOf[key]?.let(byKey::get) ?: return c.name
        return "${p.name} › ${c.name}"
    }
}
