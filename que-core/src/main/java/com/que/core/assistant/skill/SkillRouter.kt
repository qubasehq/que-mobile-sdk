package com.que.core.assistant.skill

/**
 * Registry that holds all skills and picks the right one.
 * Today there is only AutomationSkill. Future: WeatherSkill, CalendarSkill, etc.
 */
class SkillRouter {

    private val skills = mutableMapOf<String, Skill>()

    fun register(skill: Skill) {
        skills[skill.name] = skill
    }

    /**
     * For now, all AUTOMATE intents go to the "automation" skill.
     * In the future, the IntentClassifier can return a skill name in its JSON
     * and this router will dispatch accordingly.
     */
    @Suppress("UNUSED_PARAMETER")
    fun route(task: String): Skill? {
        return skills["automation"]
    }

    fun stopAll() {
        skills.values.forEach { it.stop() }
    }
}
