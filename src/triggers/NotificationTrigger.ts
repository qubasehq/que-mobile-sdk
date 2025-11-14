import { NotificationTriggerConfig, NotificationConfig } from './types'

export class NotificationTrigger {
  private config: NotificationTriggerConfig

  constructor(config: NotificationTriggerConfig) {
    this.config = config
  }

  static validateNotificationConfig(config: NotificationConfig): boolean {
    // Validate package name
    if (!config.packageName || config.packageName.trim().length === 0) {
      throw new Error('Package name is required')
    }

    // Package name should follow Android package naming convention
    const packageRegex = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/
    if (!packageRegex.test(config.packageName)) {
      throw new Error('Invalid package name format')
    }

    return true
  }

  matches(packageName: string, title: string, text: string): boolean {
    const config = this.config.notificationConfig

    // Check package name
    if (config.packageName !== packageName) {
      return false
    }

    // Check title pattern if specified
    if (config.titlePattern) {
      const titleLower = title.toLowerCase()
      const patternLower = config.titlePattern.toLowerCase()
      if (!titleLower.includes(patternLower)) {
        return false
      }
    }

    // Check text pattern if specified
    if (config.textPattern) {
      const textLower = text.toLowerCase()
      const patternLower = config.textPattern.toLowerCase()
      if (!textLower.includes(patternLower)) {
        return false
      }
    }

    return true
  }

  static createForApp(packageName: string): NotificationConfig {
    return {
      packageName
    }
  }

  static createWithTitlePattern(packageName: string, titlePattern: string): NotificationConfig {
    return {
      packageName,
      titlePattern
    }
  }

  static createWithTextPattern(packageName: string, textPattern: string): NotificationConfig {
    return {
      packageName,
      textPattern
    }
  }

  static createWithPatterns(
    packageName: string,
    titlePattern: string,
    textPattern: string
  ): NotificationConfig {
    return {
      packageName,
      titlePattern,
      textPattern
    }
  }

  getConfig(): NotificationTriggerConfig {
    return this.config
  }

  updateConfig(config: Partial<NotificationTriggerConfig>): void {
    this.config = { ...this.config, ...config }
  }
}
