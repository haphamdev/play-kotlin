package org.example

import com.chargebee.Environment
import com.chargebee.ListResult
import com.chargebee.internal.ListRequest
import com.chargebee.models.AttachedItem
import com.chargebee.models.Item
import java.io.File
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

    fun getAllPlans(env: ChargebeeEnvironment): Sequence<String> = CbItem.list()
        .type().`is`(Item.Type.PLAN)
        .status().`is`(Item.Status.ACTIVE)
        .requestAllPages(env, "Fetching all plans...")
        .mapNotNull {
            val item = it.item()
            val version = item.optInteger("cf_pricing_version")
            //Only fetch v7 plans
            if (version == null || version != 7) return@mapNotNull null
            item.id()
        }

    fun getAllAddons(env: ChargebeeEnvironment): Sequence<String> = CbItem.list()
        .type().`is`(Item.Type.ADDON)
        .status().`is`(Item.Status.ACTIVE)
        .requestAllPages(env, "Fetching all addons...")
        .mapNotNull {
            val item = it.item()
            val cfType = item.optString("cf_type")
            val version = item.optInteger("cf_pricing_version")
            //Only fetch v7 addons
            if (version == null || version != 7) return@mapNotNull null

            item.id()
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
            .request(env)

        for (addonId in addonIds) {
            AttachedItem.create(planId).itemId(addonId)
                .type(AttachedItem.Type.OPTIONAL)
                .request(env)
        }
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
