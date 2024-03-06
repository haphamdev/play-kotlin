package org.example

import com.chargebee.Environment
import com.chargebee.ListResult
import com.chargebee.internal.ListRequest
import com.chargebee.models.Coupon
import com.chargebee.models.Item
import com.chargebee.models.ItemPrice as CbItemPrice
import com.chargebee.models.Item as CbItem

class ChargeBeeClient() {
    fun getAllItemId(env: ChargebeeEnvironment): List<String> = CbItem.list()
        .status().`in`(Item.Status.ARCHIVED, Item.Status.ACTIVE)
        .requestAllPages(env)
        .map { it.item().id() }
        .toList()

    fun getAllItemPrices(env: ChargebeeEnvironment, itemIds: List<String> = emptyList()): Sequence<ItemPrice> =
        CbItemPrice.list()
            .status().`in`(CbItemPrice.Status.ARCHIVED, CbItemPrice.Status.ACTIVE)
            .also {
                if (itemIds.isNotEmpty()) {
                    it.itemId().`in`(*itemIds.toTypedArray())
                }
            }
            .requestAllPages(env)
            .map {
                val ip = it.itemPrice()
                ItemPrice(ip.id(), ip.reqString("cf_type"), ip.itemId())
            }

    fun getAllLineItemDiscounts(env: ChargebeeEnvironment): Sequence<ChargebeeDiscount> = Coupon.list()
        .status().`in`(Coupon.Status.ARCHIVED, Coupon.Status.ACTIVE)
        .applyOn().`is`(Coupon.ApplyOn.EACH_SPECIFIED_ITEM)
        .requestAllPages(env)
        .map {
            val coupon = it.coupon()
            val constraints = coupon.itemConstraints().map { it.toDomainModel() }
            ChargebeeDiscount(
                coupon.id(),
                coupon.name(),
                DiscountType.valueOf(coupon.discountType().name),
                coupon.discountAmount().toDouble(),
                ChargebeeDiscountApplyOn.EACH_SPECIFIED_ITEM,
                constraints
            )
        }

    private fun Coupon.ItemConstraint.toDomainModel(): ChargebeeItemConstraint {
        val targetType = when (constraint()) {
            Coupon.ItemConstraint.Constraint.ALL -> ChargebeeItemConstraint.TargetType.ALL
            Coupon.ItemConstraint.Constraint.SPECIFIC -> ChargebeeItemConstraint.TargetType.SPECIFIC
            else -> throw RuntimeException("Unsupported constraint type ${constraint().name} for the coupon")
        }

        val itemType = when (itemType()) {
            Coupon.ItemConstraint.ItemType.PLAN -> ChargebeeItemConstraint.ItemType.PLAN
            Coupon.ItemConstraint.ItemType.ADDON -> ChargebeeItemConstraint.ItemType.ADDON
            else -> throw RuntimeException("Unsupported item type ${itemType().name} for the coupon")
        }

        val itemIds = if (targetType == ChargebeeItemConstraint.TargetType.SPECIFIC) {
            itemPriceIds().map { it.toString() }.toSet()
        } else emptySet()

        return ChargebeeItemConstraint(targetType, itemType, itemIds)
    }

    private fun ListRequest<*>.requestAllPages(chargebeeEnvironment: ChargebeeEnvironment) =
        sequence<ListResult.Entry> {
            var nextOffset: String? = null
            do {
                val result = this@requestAllPages.limit(100).offset(nextOffset).request(chargebeeEnvironment)

                nextOffset = result.nextOffset()
                yieldAll(result)
            } while (nextOffset != null)
        }
}

data class ChargebeeDiscount(
    val id: String,
    val name: String,
    val discountType: DiscountType,
    val discountAmount: Double,
    val applyOn: ChargebeeDiscountApplyOn,
    val itemConstraints: List<ChargebeeItemConstraint> = emptyList(),
) {
    /**
     * Check if the discount is applicable for an addon.
     */
    fun isApplicable(itemPrice: ItemPrice): Boolean =
        itemConstraints.firstOrNull { it.itemType == ChargebeeItemConstraint.ItemType.ADDON }
            ?.isApplicable(itemPrice.id) ?: false

    /**
     * Check if the discount is applicable for a plan.
     */
}

data class ChargebeeItemConstraint(
    val targetType: TargetType,
    val itemType: ItemType,
    val itemIds: Set<String> = emptySet(),
) {
    /**
     * The target type of constraint.
     */
    enum class TargetType(value: String) {
        ALL("all"),
        SPECIFIC("specific"),
        OTHER("other")
    }

    /**
     * The item type that a constraint is targeting.
     */
    enum class ItemType(value: String) {
        PLAN("plan"),
        ADDON("addon"),
    }

    /**
     * Check if an item ID is allowed by the constraint.
     */
    fun isApplicable(itemId: String): Boolean = targetType == TargetType.ALL || itemIds.contains(itemId)
}

enum class DiscountType(val value: String) {
    FIXED_AMOUNT("fixed_amount"),
    PERCENTAGE("percentage"),
}

enum class ChargebeeDiscountApplyOn(val value: String) {
    INVOICE_AMOUNT("invoice_amount"),
    EACH_SPECIFIED_ITEM("each_specified_item"),
}

data class ItemPrice(
    val id: String,
    val type: String,
    val itemId: String,
)

data class ChargebeeEnvironment(
    val siteName: String,
    val apiKey: String,
) : Environment(siteName, apiKey)