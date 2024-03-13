package org.example

val ITEM_TYPE_GROUPS = listOf(
    setOf("Core", "Core_Pro", "Core_Lite"),
    setOf("Essential", "Professional", "Enterprise"),
    setOf("conversations_standard", "conversations_standard", "conversations_premium_plus", "conversations_lite"),
)

const val DRY_RUN = true

fun main() {
    val env = ChargebeeEnvironment("personio", "live_Ld9tqwNsoNlZSECTFxzUrSMdUHVZdwcdR")
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
        .filter { itemsById.contains(it.itemId) && !trialItemPriceIds.contains(it.id) }
        .groupBy { itemsById[it.itemId]!!.type }

    client.getAllLineItemDiscounts(env)
        .forEach { discount ->
            var appliedToPlan = false
            var appliedToAddon = false
            var manualCheckNeeded = false

            val updatedConstraints = discount.itemConstraints
                .map { constraint ->
                    when (constraint.itemType) {
                        ChargebeeItemConstraint.ItemType.PLAN -> {
                            appliedToPlan = true
                            val checkResult = checkPlanConstraint(constraint, discount)
                            if (checkResult.manualCheckNeeded) manualCheckNeeded = true
                            return@map checkResult.constraint
                        }

                        ChargebeeItemConstraint.ItemType.ADDON -> {
                            appliedToAddon = true
                            val checkResult = checkAddonConstraint(
                                constraint,
                                discount,
                                itemPriceIdToItemType,
                                itemPricesByTypes,
                                trialItemPriceIds
                            )

                            if (checkResult.manualCheckNeeded) manualCheckNeeded = true

                            return@map checkResult.constraint
                        }

                        ChargebeeItemConstraint.ItemType.CHARGE -> {
                            println("Coupon ${discount.id} is applied to CHARGES. Manual check needed.")
                            manualCheckNeeded = true
                            return@map constraint
                        }
                    }
                }

            if (appliedToPlan && appliedToAddon) {
                manualCheckNeeded = true
                println("Coupon ${discount.id} is applied to both PLANS and ADDONS. Manual check needed")
            }

            if (updatedConstraints.any { !discount.itemConstraints.contains(it) }) {
                if (manualCheckNeeded) {
                    println("Skip coupon ${discount.id} for manual check")
                } else if (!DRY_RUN ) {
                    println("Skip updating coupon ${discount.id} in DRY RUN mode")
                } else {
                    println("Updating coupon ${discount.id}...")
                    client.updateDiscountConstraints(env, discount.id, updatedConstraints)
                    println("Updated coupon ${discount.id}")
                }
            } else {
                println("Skip coupon ${discount.id}. No change needed")
            }

            println("----------------------------")
        }
}

fun checkPlanConstraint(constraint: ChargebeeItemConstraint, discount: ChargebeeDiscount): ConstraintCheckResult {
    when (constraint.targetType) {
        ChargebeeItemConstraint.TargetType.ALL -> {
            println("Coupon ${discount.id} is applied to all plans")
            return ConstraintCheckResult(constraint)
        }

        ChargebeeItemConstraint.TargetType.SPECIFIC -> {
            println("Coupon ${discount.id} is applied to ${constraint.itemIds.size} plans: ${constraint.itemIds}. Will be updated to All plans")
            return ConstraintCheckResult(
                ChargebeeItemConstraint(
                    targetType = ChargebeeItemConstraint.TargetType.ALL,
                    itemType = ChargebeeItemConstraint.ItemType.PLAN
                )
            )
        }

        ChargebeeItemConstraint.TargetType.CRITERIA -> {
            println("Coupon ${discount.id} has criteria constraint for plans. Manual check needed")
            return ConstraintCheckResult(constraint, true)
        }

        ChargebeeItemConstraint.TargetType.NONE -> return ConstraintCheckResult(constraint) //do nothing
    }
}

/**
 * @return the constraint and whether manual check is needed
 */
fun checkAddonConstraint(
    constraint: ChargebeeItemConstraint,
    discount: ChargebeeDiscount,
    itemPriceIdToItemType: Map<String, String>,
    itemPricesByTypes: Map<String, List<ChargebeeItemPrice>>,
    trialItemPriceIds: Set<String>
): ConstraintCheckResult {
    when (constraint.targetType) {
        ChargebeeItemConstraint.TargetType.ALL -> {
            println("Coupon ${discount.id} is applied to ALL addon. Manual check is needed")
            return ConstraintCheckResult(constraint, true)
        }

        ChargebeeItemConstraint.TargetType.SPECIFIC -> {
            val itemPriceIds = constraint.itemIds

            if (itemPriceIds.isEmpty()) {
                println("Coupon ${discount.id} is applied to specific addons but no addon is selected. Manual check needed")
                // change it to none instead when there is no item price ids, to avoid confusion
                return ConstraintCheckResult(
                    ChargebeeItemConstraint(
                        targetType = ChargebeeItemConstraint.TargetType.NONE,
                        itemType = constraint.itemType
                    ),
                    true
                )
            }

            val applicableItemTypes = itemPriceIds.mapNotNull { itemPriceIdToItemType[it] }.distinct()

            if (applicableItemTypes.size > 1) {
                println("Coupon ${discount.id} is applied to ${applicableItemTypes.size} ${constraint.itemType} types: $applicableItemTypes")
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
                println("Coupon ${discount.id} - Missing item prices: $missingItemPriceIds")
                finalItemPriceIds = finalItemPriceIds + allItemPriceIdsByTypes
            }

            if (finalItemPriceIds.any { trialItemPriceIds.contains(it) }) {
                println(
                    "Coupon ${discount.id}: Remove following free addons ${
                        finalItemPriceIds.filter {
                            trialItemPriceIds.contains(
                                it
                            )
                        }
                    }"
                )
                finalItemPriceIds = finalItemPriceIds.filter { !trialItemPriceIds.contains(it) }.toSet()
            }

            return if (finalItemPriceIds != itemPriceIds) {
                ConstraintCheckResult(constraint.copy(itemIds = finalItemPriceIds))
            } else {
                ConstraintCheckResult(constraint)
            }

        }

        ChargebeeItemConstraint.TargetType.NONE -> return ConstraintCheckResult(constraint) // do nothing
        ChargebeeItemConstraint.TargetType.CRITERIA -> {
            println("Coupon ${discount.id} has criteria constraint for addons. Manual check needed")
            return ConstraintCheckResult(constraint, true)
        }
    }
}

data class ConstraintCheckResult(
    val constraint: ChargebeeItemConstraint,
    val manualCheckNeeded: Boolean = false
)

