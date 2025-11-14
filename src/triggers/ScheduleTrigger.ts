import { NativeModules } from 'react-native'
import { ScheduleTriggerConfig, ScheduleConfig } from './types'

const { ScheduleTriggerModule } = NativeModules

export class ScheduleTrigger {
  private config: ScheduleTriggerConfig

  constructor(config: ScheduleTriggerConfig) {
    this.config = config
  }

  async schedule(): Promise<boolean> {
    try {
      const nextTime = await this.getNextExecutionTime()
      await ScheduleTriggerModule.scheduleAlarm(this.config.id, nextTime)
      return true
    } catch (error) {
      console.error('Error scheduling trigger:', error)
      throw error
    }
  }

  async cancel(): Promise<boolean> {
    try {
      await ScheduleTriggerModule.cancelAlarm(this.config.id)
      return true
    } catch (error) {
      console.error('Error cancelling trigger:', error)
      throw error
    }
  }

  async getNextExecutionTime(): Promise<number> {
    try {
      const scheduleJson = JSON.stringify(this.config.schedule)
      const nextTime = await ScheduleTriggerModule.getNextAlarmTime(
        this.config.id,
        scheduleJson
      )
      return nextTime
    } catch (error) {
      console.error('Error calculating next execution time:', error)
      throw error
    }
  }

  static validateSchedule(schedule: ScheduleConfig): boolean {
    // Validate time format (HH:mm)
    const timeRegex = /^([0-1][0-9]|2[0-3]):([0-5][0-9])$/
    if (!timeRegex.test(schedule.time)) {
      throw new Error('Invalid time format. Expected HH:mm (24-hour)')
    }

    // Validate days of week
    if (!schedule.daysOfWeek || schedule.daysOfWeek.length === 0) {
      throw new Error('At least one day of week must be specified')
    }

    for (const day of schedule.daysOfWeek) {
      if (day < 0 || day > 6) {
        throw new Error('Days of week must be between 0 (Sunday) and 6 (Saturday)')
      }
    }

    return true
  }

  static createDailySchedule(time: string): ScheduleConfig {
    return {
      time,
      daysOfWeek: [0, 1, 2, 3, 4, 5, 6]
    }
  }

  static createWeekdaySchedule(time: string): ScheduleConfig {
    return {
      time,
      daysOfWeek: [1, 2, 3, 4, 5] // Monday to Friday
    }
  }

  static createWeekendSchedule(time: string): ScheduleConfig {
    return {
      time,
      daysOfWeek: [0, 6] // Sunday and Saturday
    }
  }

  static createCustomSchedule(time: string, daysOfWeek: number[]): ScheduleConfig {
    return {
      time,
      daysOfWeek
    }
  }

  getConfig(): ScheduleTriggerConfig {
    return this.config
  }

  updateConfig(config: Partial<ScheduleTriggerConfig>): void {
    this.config = { ...this.config, ...config }
  }
}
