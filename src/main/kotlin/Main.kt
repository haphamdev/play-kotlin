package org.example

const val DRY_RUN = true

fun main() {
    val site  = System.getenv("CHARGEBEE_SITE")
    val apiKey  = System.getenv("CHARGEBEE_API_KEY")

    val chargebeeEnv = ChargebeeEnvironment(site, apiKey)
    val chargebeeClient = ChargeBeeClient()
    val addonsGroupedByVersion = chargebeeClient.getAllAddons(chargebeeEnv)
        .groupBy { it.majorVersion }

    addonsGroupedByVersion.keys.forEach { version ->
        println(
            "V$version addons:\n- " +
                    addonsGroupedByVersion[version]!!.joinToString("\n- ") { it.id }
        )
    }
    chargebeeClient.getAllPlans(chargebeeEnv)
        .forEach { plan ->
            val applicableAddonIds = addonsGroupedByVersion[plan.majorVersion]!!.map { it.id }
            println("Updating plan '${plan.id}' with v${plan.majorVersion} addons")
            if (!DRY_RUN) {
                chargebeeClient.updateApplicableAddonsForPlan(
                    env = chargebeeEnv,
                    planId = plan.id,
                    addonIds = applicableAddonIds
                )
                println("Updated plan '${plan.id}'")
            }
        }
}

