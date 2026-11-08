package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.util.HindiTransliterator

/**
 * Real Android AccessibilityService for Moko Voice Click & Cross-App Screen Reading.
 * Only active when explicitly enabled by the user in Android Accessibility Settings.
 */
class MokoAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: MokoAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On-demand node inspection is performed when the user gives a Voice Click or Screen Share command
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    /**
     * Reads visible text and content descriptions from the currently active window on screen.
     */
    fun readActiveScreenTextSummary(): String {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return ""

        val labels = LinkedHashSet<String>()
        val clickableLabels = LinkedHashSet<String>()
        collectVisibleNodes(root, labels, clickableLabels, depth = 0)
        if (labels.isEmpty() && clickableLabels.isEmpty()) return ""

        val pkgName = root.packageName?.toString().orEmpty()
        val visibleSample = labels.take(18).joinToString(" • ")
        val clickSample = clickableLabels.take(12).joinToString(", ")
        return buildString {
            if (pkgName.isNotEmpty()) append("Active App: $pkgName | ")
            if (visibleSample.isNotEmpty()) append("Visible Text: [$visibleSample] | ")
            if (clickSample.isNotEmpty()) append("Clickable Buttons: [$clickSample]")
        }.trim().trimEnd('|').trim()
    }

    private fun collectVisibleNodes(
        node: AccessibilityNodeInfo?,
        labels: MutableSet<String>,
        clickableLabels: MutableSet<String>,
        depth: Int
    ) {
        if (node == null || depth > 14 || labels.size >= 30) return
        if (!node.isVisibleToUser) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val combined = when {
            text.isNotEmpty() -> text
            desc.isNotEmpty() -> desc
            else -> ""
        }
        if (combined.length in 1..80) {
            val sanitized = HindiTransliterator.toEnglishQuery(combined).ifEmpty { combined }
            labels.add(sanitized)
            if (node.isClickable || node.parent?.isClickable == true) {
                clickableLabels.add(sanitized)
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            collectVisibleNodes(node.getChild(i), labels, clickableLabels, depth + 1)
        }
    }

    /**
     * Searches the active window for a UI element matching [targetQuery] and clicks it.
     * Supports exact match, substring match, and Hindi/Roman phonetic matching.
     */
    fun findAndClickNodeByText(targetQuery: String): Boolean {
        val cleanTarget = targetQuery.trim()
        if (cleanTarget.isEmpty()) return false

        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false

        // 1. Try built-in AccessibilityNodeInfo search first
        val directMatches = try {
            root.findAccessibilityNodeInfosByText(cleanTarget).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        for (node in directMatches) {
            if (performClickOrTapOnNode(node)) return true
        }

        // 2. Recursive phonetic & transliterated tree search
        val matchedNode = findBestMatchingNode(root, cleanTarget, depth = 0)
        if (matchedNode != null) {
            return performClickOrTapOnNode(matchedNode)
        }
        return false
    }

    private fun findBestMatchingNode(
        node: AccessibilityNodeInfo?,
        query: String,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 15) return null
        if (node.isVisibleToUser) {
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (matchesLabel(query, text) || matchesLabel(query, desc)) {
                return node
            }
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val found = findBestMatchingNode(node.getChild(i), query, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun matchesLabel(query: String, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (candidate.contains(query, ignoreCase = true) || query.contains(candidate, ignoreCase = true)) {
            return true
        }
        return HindiTransliterator.namesMatch(query, candidate)
    }

    private fun performClickOrTapOnNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 5) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
            hops++
        }

        // Fallback: dispatch tap gesture at the center of the node's screen bounds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
                val path = Path().apply {
                    moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                return dispatchGesture(gesture, null, null)
            }
        }
        return false
    }
}
