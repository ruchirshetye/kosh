package com.amitjha.kosh.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amitjha.kosh.data.CategoryEntity
import com.amitjha.kosh.data.CategoryTree
import com.amitjha.kosh.designsystem.components.CategoryIcon
import com.amitjha.kosh.designsystem.theme.KoshTheme
import com.amitjha.kosh.repo.CategoryRepository
import com.amitjha.kosh.ui.common.LocalContainer
import com.amitjha.kosh.ui.common.color
import kotlinx.coroutines.launch

/**
 * A grid of top-level categories; the selected one is outlined. Tapping a category that has subcategories (Bills &
 * Utilities) opens a row of them underneath instead of picking it: "General" picks the parent itself. When
 * [onAddSubcategory] is given, that row also offers "+ Add" to create a subcategory, which is then picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedKey: String?,
    onSelect: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onAddSubcategory: (suspend (parentKey: String, name: String) -> CategoryEntity?)? = null,
) {
    val tree = remember(categories) { CategoryTree(categories) }
    val roots = remember(categories) { tree.roots(categories) }
    val selectedRoot = selectedKey?.let(tree::rootOf)
    var expanded by remember { mutableStateOf(selectedRoot?.takeIf { tree.childrenOf(it).isNotEmpty() }) }
    var adding by remember { mutableStateOf<CategoryEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(78.dp),
            modifier = Modifier.heightIn(max = if (expanded != null) 300.dp else 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(roots, key = { it.key }) { cat ->
                val hasSubs = tree.childrenOf(cat.key).isNotEmpty()
                val selected = cat.key == selectedRoot || cat.key == expanded
                val label = if (selectedKey != null && selectedKey != cat.key && cat.key == selectedRoot) {
                    tree[selectedKey]?.name ?: cat.name
                } else cat.name
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) cat.color().copy(alpha = 0.18f) else KoshTheme.colors.cardSurface,
                    border = if (selected) BorderStroke(1.5.dp, cat.color()) else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (hasSubs) expanded = if (expanded == cat.key) null else cat.key
                            else onSelect(cat)
                        },
                ) {
                    Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CategoryIcon(cat.icon, cat.color(), size = 38.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (hasSubs) "$label ▾" else label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        val parent = expanded?.let(tree::get)
        if (parent != null) {
            Spacer(Modifier.height(12.dp))
            Text(parent.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedKey == parent.key,
                    onClick = { onSelect(parent) },
                    label = { Text("General") },
                )
                tree.childrenOf(parent.key).forEach { sub ->
                    FilterChip(
                        selected = selectedKey == sub.key,
                        onClick = { onSelect(sub) },
                        label = { Text(sub.name) },
                        leadingIcon = { CategoryIcon(sub.icon, sub.color(), size = 20.dp) },
                    )
                }
                if (onAddSubcategory != null) {
                    AssistChip(onClick = { adding = parent }, label = { Text("+ Add") })
                }
            }
        }
    }

    val addTo = adding
    if (addTo != null && onAddSubcategory != null) {
        var name by remember(addTo.key) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("New in ${addTo.name}") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CategoryRepository.MAX_NAME) },
                    label = { Text("Name, e.g. Society parking") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = name
                    adding = null
                    scope.launch { onAddSubcategory(addTo.key, n)?.let(onSelect) }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}

/** Creates a subcategory with the app's [CategoryRepository] and returns it, for [CategoryGrid]'s "+ Add". */
@Composable
fun rememberAddSubcategory(): suspend (String, String) -> CategoryEntity? {
    val categories = LocalContainer.current.categories
    return remember(categories) { { parent, name -> categories.byKey(categories.addSubcategory(parent, name)) } }
}

/**
 * Bottom sheet to recategorise a transaction, with an "apply to all from this merchant" switch
 * that also teaches future imports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecategoriseSheet(
    categories: List<CategoryEntity>,
    currentKey: String?,
    merchant: String?,
    onDismiss: () -> Unit,
    onPick: (key: String, applyToSimilar: Boolean) -> Unit,
) {
    var applyAll by remember { mutableStateOf(merchant != null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Choose a category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (merchant != null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Apply to all from $merchant", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Future imports will follow this too", style = MaterialTheme.typography.bodySmall, color = KoshTheme.colors.subtleText)
                    }
                    Switch(checked = applyAll, onCheckedChange = { applyAll = it })
                }
            }
            CategoryGrid(categories, currentKey, onSelect = { onPick(it.key, applyAll) }, onAddSubcategory = rememberAddSubcategory())
            Spacer(Modifier.height(16.dp))
        }
    }
}

