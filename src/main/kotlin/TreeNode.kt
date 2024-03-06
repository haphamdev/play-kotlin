package org.example

import java.util.*

class TreeNode(var `val`: Int) {
    var left: TreeNode? = null
    var right: TreeNode? = null
    override fun toString(): String {
        return "($`val` (L: ${left?.`val`}, R: ${right?.`val`})"
    }

    fun traverseList(): List<Int> {
        val result = mutableListOf<Int>()
        traverseList(this, result)
        return result
    }

    private fun traverseList(node: TreeNode?, result: MutableList<Int>) {
        if (node == null) return
        node.left?.also { traverseList(it, result) }
        result.add(node.`val`)
        node.right?.also { traverseList(it, result) }
    }

    companion object {
        fun fromList(list: List<Int?>): TreeNode {
            val traverseList: Queue<Int> = LinkedList(list)
            val queue: Queue<TreeNode> = LinkedList()
            var root: TreeNode? = null
            var current: TreeNode

            while (traverseList.isNotEmpty()) {
                if (queue.isEmpty()) {
                    current = TreeNode(traverseList.poll())
                    root = current
                } else {
                    current = queue.poll()
                }

                val l = traverseList.poll()
                l?.also {
                    current.left = TreeNode(it)
                    queue.add(current.left)
                }

                val r = traverseList.poll()
                r?.also {
                    current.right = TreeNode(it)
                    queue.add(current.right)
                }
            }
            return root!!
        }
    }
}