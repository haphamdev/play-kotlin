package org.example

import java.util.*
import kotlin.math.max

/**
 * Example:
 * var li = ListNode(5)
 * var v = li.`val`
 * Definition for singly-linked list.
 * class ListNode(var `val`: Int) {
 *     var next: ListNode? = null
 * }
 */
class Solution {
    val result : TreeNode? = null
    val list : Queue<Int> = LinkedList()
    fun flatten(root: TreeNode?): Unit {
        if (root == null) return
        doFlatten(root)
        list.poll()
        var current: TreeNode? = root
        while (list.isNotEmpty()) {
            current!!.right = TreeNode(list.poll())
            current = current.right
        }
    }

    private fun doFlatten(node: TreeNode) {
        list.add(node.`val`)
        if (node.left != null) doFlatten(node.left!!)
        if (node.right != null) doFlatten(node.right!!)
    }

    private fun getDepth(root: TreeNode?): Int {
        if (root == null) return 0
        if (root.left == null && root.right == null) return 1
        return max(getDepth(root.left), getDepth(root.right)) + 1
    }

    private fun isMirror(node1: TreeNode?, node2: TreeNode?): Boolean {
        if (node1 == null || node2 == null) return node1 == node2
        return node1.`val` == node2.`val` && isMirror(node1.left, node2.right) && isMirror(node1.right, node2.left)
    }
}