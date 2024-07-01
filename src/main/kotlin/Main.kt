package org.example

import java.io.File
import kotlin.io.path.fileVisitor

const val DRY_RUN = false

fun main() {
    val site  = System.getenv("CHARGEBEE_SITE")
    val apiKey  = System.getenv("CHARGEBEE_API_KEY")

    if (site != "personio-test") {
        println("🔥Site: $site. Please check again")
    } else {
        println("Running for site $site")
    }

    val chargebeeEnv = ChargebeeEnvironment(site, apiKey)
    val chargebeeClient = ChargeBeeClient()
    val addonIds = chargebeeClient.getAllAddons(chargebeeEnv).toList()

    val file = File("output.txt")
    file.writeText("item[id],attached_item[item_id],attached_item[type]")
    chargebeeClient.getAllPlans(chargebeeEnv)
        .forEach { plan ->
            println("Updating plan $plan")
            if (!DRY_RUN) {
                chargebeeClient.updateApplicableAddonsForPlan(
                    env = chargebeeEnv,
                    planId = plan,
                    addonIds = addonIds
                )
                println("Updated plan '$plan'")
            }
        }
}

