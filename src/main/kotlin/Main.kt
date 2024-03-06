package org.example

fun main() {
    val env = ChargebeeEnvironment("site", "the-key")
    val client = ChargeBeeClient()

    val items = client.getAllItems(env)
    val itemsToItemTypes = items.groupBy { it.type }
    val itemPrices = client.getAllItemPrices(env, items.map { it.id }.toSet())
    val itemPriceIdToItemType = itemPrices.map { it.id to itemsToItemTypes[it.itemId] }.toMap()
    val itemPricesByTypes = itemPrices.groupBy { itemsToItemTypes[it.itemId] }

    client.getAllLineItemDiscounts(env)
        .forEach { discount ->
            var skip = false
            discount.itemConstraints
                .forEach { constraint ->
                    if (constraint.targetType == ChargebeeItemConstraint.TargetType.ALL) {
                        skip = true
                        println("Coupon ${discount.id} is applied to all ${constraint.itemType}s")
                    }
                }
            if (skip) return@forEach

            val itemPriceIds = discount.itemConstraints.flatMap { constraint ->
                constraint.itemIds
            }.toSet()

            val applicableItemTypes = itemPriceIds.map { itemPriceIdToItemType[it] }.distinct()

            if (applicableItemTypes.size > 1) {
                println("Coupon $discount.id is applied to multiple item types: $applicableItemTypes")
            }

            applicableItemTypes.forEach { itemType ->
                val allItemPriceIdsOfType = itemPricesByTypes[itemType]?.map {
                    it.id
                } ?: emptyList()

                // Find out the missing items
                val missingItemPriceIds = allItemPriceIdsOfType - itemPriceIds
                if (missingItemPriceIds.isNotEmpty()) {
                    println("Coupon ${discount.id}: Missing item prices: $missingItemPriceIds")
                }
            }

        }
}

