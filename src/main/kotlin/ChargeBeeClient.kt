package org.example

import com.chargebee.models.Item as CbItem
import com.chargebee.models.ItemPrice as CbItemPrice
import com.chargebee.Environment
import com.chargebee.ListResult
import com.chargebee.internal.ListRequest
import com.chargebee.models.Coupon
import com.chargebee.models.Item
import com.chargebee.org.json.JSONArray

class ChargeBeeClient() {
    companion object {
        val BLACKLIST_COUPON_IDS = listOf(
            "9%_discount_excl_epdv2",
            "38%_discount_excl_epd_and_datev_and_rec",
            "38%_discount_excl_epd_and_datev",
            "35%_discount_excl_conversations",
            "20%_discount_excl_datev",
            "23%_discount_excl_conversations",
            "30%_discount_excl_epd_v3",
            "100%_discount_excl_epd_2",
            "100%_discount_excl_epd",
            "38%_discount_excl_epd",
            "41%_discount_excl_conversations",
            "42%_discount_excl_epd",
            "10%_discount_excl_epd_new",
            "15%_discount_excl_epd_v3",
            "25%_discount_excl_epd_v3",
            "57%_discount_excl_epd",
            "43%_discount_excl_datev",
            "9%_discount_excl_epd",
            "18%_discount_excl_epd",
            "32.5%_discount_excl_epd",
            "35%_discount_excl_epd",
            "30%_discount_excl_epd_v2",
            "27%_discount_excl_epd",
            "33%_discount_excl_epd",
            "56%_discount_excl_epd",
            "50%_discount_excl_epd",
            "40%_discount_excl_epd",
            "10%_discount_excl_epd",
            "5%_discount_excl_epd",
            "30%_discount_excl_epd",
            "25%_discount_excl_epd_v2",
            "20%_discount_excl_epd_v2",
            "15%_discount_excl_epd_v2",
            "8%_discount_excl_epd_v2",
            "20%_discount_excl_epd_v1",
            "8%_discount_excl_epd_temp",
            "25%_discount_excl_epd_temp",
            "15%_discount_excl_epd_temp",
            "20%_discount_excl_epd_temp",
            "35%DISCOUNTEXCLSURVEY",
            "10%DISCOUNT(EXCL.EPD)V2",
            "40%DISCOUNT(EXCL.EPD)V2",
            "52.5%DISCOUNT(EXCL.EPD)SAETA",
            "42.29%DISCOUNT(EXCL.EPD)",
        )
    }
    fun getAllItems(env: ChargebeeEnvironment): List<ChargebeeItem> = CbItem.list()
        .status().`in`(Item.Status.ARCHIVED, Item.Status.ACTIVE)
        .requestAllPages(env, "Fetching all items...")
        .filter { !it.item().optString("cf_type").isNullOrEmpty() }
        .map {
            ChargebeeItem(
                id = it.item().id(),
                type = it.item().reqString("cf_type"),
                isTrial = it.item().optString("cf_special_offer") == "trial",
            )
        }
        .toList()

    fun getAllItemPrices(env: ChargebeeEnvironment, itemIds: Set<String> = emptySet()): Sequence<ChargebeeItemPrice> =
        CbItemPrice.list()
            .status().`in`(CbItemPrice.Status.ARCHIVED, CbItemPrice.Status.ACTIVE)
            .requestAllPages(env, "Fetching all Item prices...")
            .filter {
                itemIds.isEmpty() || itemIds.contains(it.itemPrice().itemId())
            }
            .map {
                val ip = it.itemPrice()

                ChargebeeItemPrice(
                    id = ip.id(),
                    itemId = ip.itemId(),
                    isFree = ip.price() == 0L
                        || (
                        ip.price() == null
                            && ip.tiers().all { tier -> tier.price() == null || tier.price() == 0L }
                        ),
                )
            }

    fun getAllLineItemDiscounts(env: ChargebeeEnvironment): Sequence<ChargebeeDiscount> = Coupon.list()
        .status().`in`(Coupon.Status.ARCHIVED, Coupon.Status.ACTIVE)
        .applyOn().`is`(Coupon.ApplyOn.EACH_SPECIFIED_ITEM)
        .requestAllPages(env, "Fetching all Line-item discounts...")
        .mapNotNull {
            val coupon = it.coupon()

            if (BLACKLIST_COUPON_IDS.contains(coupon.id())) {
                return@mapNotNull null
            }
            val constraints = coupon.itemConstraints().mapNotNull { it.toDomainModel() }
            val discountType = DiscountType.valueOf(coupon.discountType().name)
            val discountValue = if (discountType == DiscountType.FIXED_AMOUNT) {
                coupon.discountAmount().toDouble()
            } else {
                coupon.discountPercentage()
            }

            ChargebeeDiscount(
                coupon.id(),
                coupon.name(),
                discountType,
                discountValue,
                ChargebeeDiscountApplyOn.EACH_SPECIFIED_ITEM,
                constraints
            )
        }

    private fun Coupon.ItemConstraint.toDomainModel(): ChargebeeItemConstraint? {
        val targetType = ChargebeeItemConstraint.TargetType.valueOf(constraint().name)
        val itemType = ChargebeeItemConstraint.ItemType.valueOf(itemType().name)

        val itemIds = if (targetType == ChargebeeItemConstraint.TargetType.SPECIFIC) {
            itemPriceIds()?.map { it.toString() }?.toSet() ?: emptySet()
        } else emptySet()

        return ChargebeeItemConstraint(targetType, itemType, itemIds)
    }

    private fun ListRequest<*>.requestAllPages(chargebeeEnvironment: ChargebeeEnvironment, msg: String = "Receiving result from ChargeBee") =
        sequence<ListResult.Entry> {
            var nextOffset: String? = null
            do {
                val result = this@requestAllPages.limit(100).offset(nextOffset).request(chargebeeEnvironment)
                println(msg)

                nextOffset = result.nextOffset()
                yieldAll(result)
            } while (nextOffset != null)
        }

    fun updateDiscountConstraints(
        env: ChargebeeEnvironment,
        discountId: String,
        constraints: List<ChargebeeItemConstraint>,
    ) {
        Coupon.updateForItems(discountId).apply {
            constraints.forEachIndexed { idx, constraint ->
                this.itemConstraintItemType(idx, Coupon.ItemConstraint.ItemType.valueOf(constraint.itemType.name))
                this.itemConstraintConstraint(idx, Coupon.ItemConstraint.Constraint.valueOf(constraint.targetType.name))
                if (constraint.targetType == ChargebeeItemConstraint.TargetType.SPECIFIC) {
                    this.itemConstraintItemPriceIds(idx, JSONArray().putAll(constraint.itemIds))
                }
            }
        }.request(env)

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
    fun isApplicable(itemPrice: ChargebeeItemPrice): Boolean =
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
        NONE("none"),
        CRITERIA("criteria"),
    }

    /**
     * The item type that a constraint is targeting.
     */
    enum class ItemType(value: String) {
        PLAN("plan"),
        ADDON("addon"),
        CHARGE("charge"),
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

data class ChargebeeItemPrice(
    val id: String,
    val itemId: String,
    val isFree: Boolean,
)

data class ChargebeeItem(
    val id: String,
    val type: String,
    val isTrial: Boolean,
)

data class ChargebeeEnvironment(
    val siteName: String,
    val apiKey: String,
) : Environment(siteName, apiKey)
