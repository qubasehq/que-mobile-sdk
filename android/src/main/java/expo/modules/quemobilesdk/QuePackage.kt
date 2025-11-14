package expo.modules.quemobilesdk

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModulesProvider
import expo.modules.quemobilesdk.triggers.ScheduleTriggerModule
import expo.modules.quemobilesdk.triggers.TriggerExecutionModule

class QuePackage : ModulesProvider {
    override fun getModulesList(): List<Class<out Module>> {
        return listOf(
            QueMobileSdkModule::class.java,
            AccessibilityModule::class.java,
            ScheduleTriggerModule::class.java,
            TriggerExecutionModule::class.java
        )
    }
}
