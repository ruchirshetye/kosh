package com.amitjha.kosh.repo

import com.amitjha.kosh.data.CategoryEntity
import com.amitjha.kosh.data.CategoryTree
import com.amitjha.kosh.data.KoshDatabase
import com.amitjha.kosh.model.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val db: KoshDatabase) {

    fun observeAll(): Flow<List<CategoryEntity>> = db.categories().observeAll()

    fun observeByType(type: TxnType): Flow<List<CategoryEntity>> = observeAll().map { list -> list.filter { it.type == type } }

    fun observeByKey(): Flow<Map<String, CategoryEntity>> = observeAll().map { list -> list.associateBy { it.key } }

    suspend fun all(): List<CategoryEntity> = db.categories().all()

    suspend fun byKey(key: String): CategoryEntity? = all().firstOrNull { it.key == key }

    fun observeTree(): Flow<CategoryTree> = observeAll().map(::CategoryTree)

    /** Adds a custom category; the key is derived from the name. */
    suspend fun addCustom(name: String, type: TxnType, icon: String, colorArgb: Long): String =
        insertCustom(name, type, icon, colorArgb, parentKey = null)

    /**
     * Adds a custom subcategory under [parentKey], taking the parent's type, icon and colour. Returns the new key, or the
     * existing key when the parent already has a subcategory with this name. Subcategories are one level deep, so a
     * subcategory's own key is not a valid parent: its parent is used instead.
     */
    suspend fun addSubcategory(parentKey: String, name: String): String {
        val cats = all()
        val tree = CategoryTree(cats)
        val parent = tree[tree.rootOf(parentKey)] ?: error("Unknown category $parentKey")
        val clean = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)
        require(clean.isNotEmpty()) { "Name is empty" }
        tree.childrenOf(parent.key).firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.key }
        return insertCustom(clean, parent.type, parent.icon, parent.colorArgb, parent.key)
    }

    private suspend fun insertCustom(name: String, type: TxnType, icon: String, colorArgb: Long, parentKey: String?): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "custom" }
        val existing = all().map { it.key }.toSet()
        val stem = if (parentKey == null) "custom_$base" else "custom_${parentKey}_$base"
        var key = stem
        var n = 2
        while (key in existing) key = "${stem}_${n++}"
        db.categories().upsert(
            CategoryEntity(key = key, name = name, type = type, icon = icon, colorArgb = colorArgb, isDefault = false, parentKey = parentKey),
        )
        return key
    }

    companion object {
        const val MAX_NAME = 30
    }
}
