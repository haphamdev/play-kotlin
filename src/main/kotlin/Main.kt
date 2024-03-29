package org.example

val ITEM_TYPE_GROUPS = listOf(
    setOf("Core", "Core_Pro", "Core_Lite"),
    setOf("Essential", "Professional", "Enterprise"),
    setOf("conversations_standard", "conversations_standard", "conversations_premium_plus", "conversations_lite"),
)

const val DRY_RUN = true

fun main() {
    val env = ChargebeeEnvironment("personio-site", "key")
    val client = ChargeBeeClient()

    val items = client.getAllItems(env)
    val itemsById = items.associateBy { it.id }
    val itemPrices = client.getAllItemPrices(env, items.map { it.id }.toSet())
    val itemPriceIdToItemType = itemPrices
        .filter { itemsById.containsKey(it.itemId) }
        .map { it.id to itemsById[it.itemId]!!.type }
        .toMap()

    val trialItemPriceIds = itemPrices
        .filter { it.isFree || (itemsById[it.itemId]?.isTrial ?: false) }
        .map { it.id }
        .toSet()

    val itemPricesByTypes = itemPrices
        .filter { itemsById.contains(it.itemId) }
        .filter { !trialItemPriceIds.contains(it.id) }
        .groupBy { itemsById[it.itemId]!!.type }

    client.getAllLineItemDiscounts(env)
        .forEach { discount ->
            val updatedConstraints = discount.itemConstraints
                .map { constraint ->
                    if (constraint.targetType == ChargebeeItemConstraint.TargetType.ALL
                    ) {
                        println("Coupon ${discount.id} with constraint ${constraint.itemType} is applied to all ${constraint.itemType}s")
                        constraint
                    } else if (constraint.targetType == ChargebeeItemConstraint.TargetType.NONE) {
                        constraint
                    } else {
                        val itemPriceIds = constraint.itemIds

                        if (itemPriceIds.isEmpty()) {
                            // change it to none instead when there is no item price ids, to avoid confusion
                            return@map ChargebeeItemConstraint(
                                targetType = ChargebeeItemConstraint.TargetType.NONE,
                                itemType = constraint.itemType
                            )
                        }

                        if (constraint.itemType == ChargebeeItemConstraint.ItemType.PLAN) {
                            println("Coupon ${discount.id} with constraint ${constraint.itemType} is applied to all plan")
                            return@map ChargebeeItemConstraint(
                                targetType = ChargebeeItemConstraint.TargetType.ALL,
                                itemType = constraint.itemType
                            )
                        }

                        val applicableItemTypes = itemPriceIds.mapNotNull { itemPriceIdToItemType[it] }.distinct()

                        if (applicableItemTypes.size > 1) {
                            println("Coupon ${discount.id} with constraint ${constraint.itemType} is applied to multiple item types: $applicableItemTypes")
                        }

                        val allItemPriceIdsByTypes = applicableItemTypes.flatMap { itemType ->
                            val itemTypeGroup =
                                ITEM_TYPE_GROUPS.firstOrNull { it.contains(itemType) } ?: setOf(itemType)

                            itemTypeGroup.flatMap { subItemType ->
                                itemPricesByTypes[subItemType]?.map {
                                    it.id
                                } ?: emptyList()
                            }
                        }.toSet()

                        var finalItemPriceIds = itemPriceIds

                        // Find out the missing items
                        val missingItemPriceIds = allItemPriceIdsByTypes - itemPriceIds
                        if (missingItemPriceIds.isNotEmpty()) {
                            println("Coupon ${discount.id} with constraint ${constraint.itemType}: Missing item prices: $missingItemPriceIds")
                            finalItemPriceIds = finalItemPriceIds + allItemPriceIdsByTypes
                        }

                        if (finalItemPriceIds.any { trialItemPriceIds.contains(it) }) {
                            println(
                                "Remove following items on Coupon ${discount.id} with constraint ${constraint.itemType}: Contains trial or free item ${
                                    finalItemPriceIds.filter {
                                        trialItemPriceIds.contains(
                                            it
                                        )
                                    }
                                }"
                            )
                            finalItemPriceIds = finalItemPriceIds.filter { !trialItemPriceIds.contains(it) }.toSet()
                        }

                        if (finalItemPriceIds != itemPriceIds) {
                            constraint.copy(itemIds = finalItemPriceIds)
                        } else {
                            constraint
                        }
                    }
                }

            if (updatedConstraints.any { !discount.itemConstraints.contains(it) }) {
                println("Updating coupon ${discount.id} constraints")
                if (!DRY_RUN) {
                    client.updateDiscountConstraints(env, discount.id, updatedConstraints)
                    println("Updated coupon ${discount.id} constraints")
                }
                println("----------------------------")
            } else {
                println("Skip updating coupon ${discount.id}")
                println("----------------------------")
            }

        }
}

