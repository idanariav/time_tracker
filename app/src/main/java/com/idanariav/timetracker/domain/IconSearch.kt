package com.idanariav.timetracker.domain

/** Search metadata for one icon (from assets/lucide_icons.tsv). */
data class IconMeta(val name: String, val categories: List<String>, val tags: List<String>)

data class IconCatalogData(val icons: List<IconMeta>, val categories: List<Pair<String, String>>) // id to title

/** Parses "#cat<TAB>id<TAB>Title" lines and "Name<TAB>cat,cat<TAB>tag,tag" lines. */
fun parseIconCatalog(lines: Sequence<String>): IconCatalogData {
    val icons = mutableListOf<IconMeta>()
    val cats = mutableListOf<Pair<String, String>>()
    for (line in lines) {
        if (line.isBlank()) continue
        val f = line.split('\t')
        if (f[0] == "#cat") {
            if (f.size >= 3) cats += f[1] to f[2]
        } else {
            fun list(i: Int) = f.getOrNull(i).orEmpty().split(',').filter { it.isNotBlank() }
            icons += IconMeta(f[0], list(1), list(2))
        }
    }
    return IconCatalogData(icons, cats)
}

private fun tokens(query: String): List<String> =
    query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }

/** Lower is better; null = no match. Name matches beat tag matches. */
private fun score(icon: IconMeta, token: String): Int? {
    val name = icon.name.lowercase()
    return when {
        name == token -> 0
        name.startsWith(token) -> 1
        name.contains(token) -> 2
        icon.tags.any { it == token } -> 3
        icon.tags.any { tag -> tag.split(' ').any { it.startsWith(token) } } -> 4
        token.length >= 4 && icon.tags.any { it.contains(token) } -> 5
        else -> null
    }
}

/**
 * Icon names matching any word of [query] (by name, then by tag), best first; restricted to
 * [category] when given. A blank query returns the category's icons (or all) alphabetically.
 */
fun searchIcons(icons: List<IconMeta>, query: String, category: String? = null): List<String> {
    val pool = if (category == null) icons else icons.filter { category in it.categories }
    val words = tokens(query)
    if (words.isEmpty()) return pool.map { it.name }
    return pool.mapNotNull { icon -> words.mapNotNull { score(icon, it) }.minOrNull()?.let { icon to it } }
        .sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name }))
        .map { it.first.name }
}
