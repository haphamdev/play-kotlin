package org.example

import com.chargebee.models.Export.ItemPricesRequest

fun main() {
    val env = ChargebeeEnvironment("site", "the-key")
    val client = ChargeBeeClient()

    val itemIds = client.getAllItemId(env)
    val itemPrices = client.getAllItemPrices(env, itemIds)
    val itemPriceIdToItemType = itemPrices.map { it.id to it.type }.toMap()
    val itemPricesByTypes = itemPrices.groupBy { it.type }

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

            if (applicableItemTypes.size> 1) {
                println("Coupon $discount.id is applied to multiple item types: $applicableItemTypes")
            }

            applicableItemTypes.forEach {itemType ->
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

