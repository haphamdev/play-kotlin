package org.example

import com.chargebee.Environment
import com.chargebee.ListResult
import com.chargebee.internal.ListRequest
import com.chargebee.models.Item
import com.chargebee.models.Item as CbItem

class ChargeBeeClient {
    companion object {
        val BLACKLIST_PLAN_TYPES = listOf(
            "Key_Account_Management",
            "job_posting_bundle",
            "Success_Plans",
            "Premium_Support"
        )

        val BLACKLIST_ADDON_TYPES = listOf(
            "setup",
            "Key_Account_Management",
            "job_posting_bundle",
            "personio_green",
            "prof_services",
            "volume_based_job_postings"
        )
    }

    fun getAllPlans(env: ChargebeeEnvironment): Sequence<ChargebeeItem> = CbItem.list()
        .type().`is`(Item.Type.PLAN)
        .status().`is`(Item.Status.ACTIVE)
        .requestAllPages(env, "Fetching all plans...")
        .mapNotNull {
            val item = it.item()
            val cfType = item.optString("cf_type")
            val version = item.optInteger("cf_pricing_version")
            if (cfType.isNullOrEmpty() ||
                BLACKLIST_PLAN_TYPES.contains(cfType) ||
                version == null ||
                version < 5 || version > 6
            ) return@mapNotNull null
            ChargebeeItem(
                id = item.id(),
                type = cfType,
                isTrial = it.item().optString("cf_special_offer") == "trial",
                majorVersion = version,
            )
        }

    fun getAllAddons(env: ChargebeeEnvironment): Sequence<ChargebeeItem> = CbItem.list()
        .type().`is`(Item.Type.ADDON)
        .status().`is`(Item.Status.ACTIVE)
        .requestAllPages(env, "Fetching all addons...")
        .mapNotNull {
            val item = it.item()
            val cfType = item.optString("cf_type")
            val version = item.optInteger("cf_pricing_version")
            if (
                cfType.isNullOrEmpty() ||
                BLACKLIST_ADDON_TYPES.contains(cfType) ||
                version == null ||
                version < 5 || version > 6
            ) return@mapNotNull null
            ChargebeeItem(
                id = item.id(),
                type = cfType,
                isTrial = it.item().optString("cf_special_offer") == "trial",
                majorVersion = version,
            )
        }


    private fun ListRequest<*>.requestAllPages(
        chargebeeEnvironment: ChargebeeEnvironment,
        msg: String = "Receiving result from ChargeBee"
    ) =
        sequence<ListResult.Entry> {
            var nextOffset: String? = null
            do {
                val result = this@requestAllPages.limit(100).offset(nextOffset).request(chargebeeEnvironment)
                println(msg)

                nextOffset = result.nextOffset()
                yieldAll(result)
            } while (nextOffset != null)
        }


    fun updateApplicableAddonsForPlan(
        env: ChargebeeEnvironment,
        planId: String,
        addonIds: List<String>
    ) {
        CbItem.update(planId)
            .itemApplicability(Item.ItemApplicability.RESTRICTED)
            .applicableItems(*addonIds.toTypedArray())
            .request(env)
    }
}

data class ChargebeeItem(
    val id: String,
    val type: String,
    val isTrial: Boolean,
    val majorVersion: Int,
)

data class ChargebeeEnvironment(
    val siteName: String,
    val apiKey: String,
) : Environment(siteName, apiKey)
