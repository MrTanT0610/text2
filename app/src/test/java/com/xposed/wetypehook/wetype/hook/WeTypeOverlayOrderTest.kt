package com.xposed.wetypehook.wetype.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeTypeOverlayOrderTest {
    private class Node(var z: Float = 0f) {
        var parent: Node? = null
        val children = mutableListOf<Node>()

        fun add(child: Node = Node()): Node {
            child.parent?.children?.remove(child)
            child.parent = this
            children += child
            return child
        }
    }

    private val container = Node()
    private val inputLayer = container.add()
    private val candidates = inputLayer.add()
    private val keyboard = inputLayer.add()
    private val clipboardLayer = container.add()
    private val clipboard = clipboardLayer.add()
    private val confirmationLayer = container.add()
    private val confirmation = confirmationLayer.add()

    @Test
    fun clipboardHidesInputButPreservesClearConfirmationAboveIt() {
        assertTrue(isBehind(keyboard, clipboard))
        assertTrue(isBehind(candidates, clipboard))
        assertFalse(isBehind(confirmation, clipboard))
    }

    @Test
    fun confirmationCoversClipboardWithoutCoveringItself() {
        assertTrue(isBehind(clipboard, confirmation))
        assertTrue(isBehind(keyboard, confirmation))
        assertTrue(isBehind(candidates, confirmation))
        assertFalse(isBehind(confirmation, confirmation))
        // Once the modal closes, the clipboard is restored while the input stays hidden.
        assertFalse(isBehind(clipboard, clipboard))
        assertTrue(isBehind(keyboard, clipboard))
    }

    @Test
    fun comparesLayerOrderInsteadOfKeyboardOrderInsideEachLayer() {
        inputLayer.add()
        inputLayer.add(keyboard)
        assertTrue(isBehind(keyboard, clipboard))
        assertFalse(isBehind(confirmation, clipboard))
    }

    @Test
    fun neverHidesAnOverlayItsContentsOrItsAncestors() {
        assertFalse(isBehind(clipboard, clipboard))
        assertFalse(isBehind(clipboard.add(), clipboard))
        assertFalse(isBehind(clipboardLayer, clipboard))
        assertFalse(isBehind(container, clipboard))
    }

    @Test
    fun handlesOverlaysSharingTheSameParent() {
        val nestedOverlay = clipboardLayer.add()
        assertTrue(isBehind(clipboard, nestedOverlay))
        assertFalse(isBehind(nestedOverlay, clipboard))
    }

    @Test
    fun elevationAndTranslationZTakePrecedenceOverChildOrder() {
        inputLayer.z = 2f
        assertFalse(isBehind(keyboard, clipboard))
        confirmationLayer.z = -1f
        assertTrue(isBehind(confirmation, clipboard))
    }

    @Test
    fun childElevationCannotLiftItAboveItsParentLayer() {
        keyboard.z = 10f
        assertTrue(isBehind(keyboard, clipboard))
    }

    @Test
    fun reevaluatesReparentedAndReorderedKeyboards() {
        assertTrue(isBehind(keyboard, clipboard))
        confirmationLayer.add(keyboard)
        assertFalse(isBehind(keyboard, clipboard))
        inputLayer.add(keyboard)
        assertTrue(isBehind(keyboard, clipboard))
        container.add(inputLayer)
        assertFalse(isBehind(keyboard, clipboard))
    }

    @Test
    fun ignoresViewsAndOverlaysOutsideTheTrackedContainer() {
        val detached = Node()
        assertFalse(isBehind(detached, clipboard))
        assertFalse(isBehind(keyboard, detached))
        assertFalse(isBehind(keyboard, container))
    }

    private fun isBehind(view: Node, overlay: Node): Boolean = isWeTypeViewBehindOverlay(
        view, overlay, container,
        parentOf = { it.parent },
        compareSiblings = { parent, first, second ->
            val zOrder = first.z.compareTo(second.z)
            if (zOrder != 0) zOrder else {
                parent.children.indexOf(first).compareTo(parent.children.indexOf(second))
            }
        }
    )
}
