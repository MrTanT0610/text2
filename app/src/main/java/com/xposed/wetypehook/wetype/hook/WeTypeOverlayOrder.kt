package com.xposed.wetypehook.wetype.hook

/** Compares the first distinct branches below their common ancestor, never ancestor contents. */
internal fun <T : Any> isWeTypeViewBehindOverlay(
    view: T,
    overlay: T,
    container: T,
    parentOf: (T) -> T?,
    compareSiblings: (parent: T, first: T, second: T) -> Int
): Boolean {
    fun pathFromContainer(target: T): List<T>? {
        val path = mutableListOf<T>()
        var current: T? = target
        while (current !== container) {
            val node = current ?: return null
            path += node
            current = parentOf(node)
        }
        return path.asReversed()
    }

    val viewPath = pathFromContainer(view) ?: return false
    val overlayPath = pathFromContainer(overlay) ?: return false
    var parent = container
    for (index in 0 until minOf(viewPath.size, overlayPath.size)) {
        val viewBranch = viewPath[index]
        val overlayBranch = overlayPath[index]
        if (viewBranch !== overlayBranch) {
            return compareSiblings(parent, viewBranch, overlayBranch) < 0
        }
        parent = viewBranch
    }
    // Hiding an overlay's ancestor or one of its own children would hide the overlay itself.
    return false
}
