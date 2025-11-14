# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-11-14

### Added

#### Core Features
- **Agent Core Loop**: Implemented SENSE → THINK → ACT autonomous agent loop
- **Native Android Bridge**: Accessibility Service integration for UI hierarchy and interaction
- **TypeScript-First API**: Full TypeScript support with discriminated union types
- **React Hooks**: `useAgent`, `useVoice`, and `useFileSystem` hooks for seamless React Native integration
- **Expo Config Plugin**: Zero-config setup for Expo managed workflow

#### Perception System
- XML UI hierarchy parsing with element deduplication
- Screen state analysis with keyboard detection
- Activity tracking and scroll indicators
- Element mapping with numeric IDs for LLM interaction

#### Memory & Context Management
- MemoryManager for conversation history and prompt building
- FileSystem for sandboxed task tracking (todo.md, results.md)
- History truncation for context window management
- One-time read state tracking for file contents

#### LLM Integration
- Gemini API client with structured output parsing
- Automatic retry with corrective prompts on parse failures
- Support for 19 action types with type-safe interfaces
- Error recovery and consecutive failure tracking

#### Action Execution
- **UI Actions**: tap_element, long_press_element, type, swipe_up, swipe_down
- **Navigation**: back, home, switch_app, open_app
- **System**: wait, search_google, launch_intent
- **Voice**: speak, ask (TTS/STT integration)
- **File Operations**: write_file, append_file, read_file
- **Completion**: done action with success flag and attachments

#### Voice Integration
- Text-to-Speech (TTS) via react-native-tts
- Speech-to-Text (STT) via react-native-voice
- Voice command support with ask action
- Voice notifications for task completion

#### React Components
- **AgentProvider**: Context provider for agent configuration
- **AgentButton**: Quick action button component
- **DebugOverlay**: Real-time debugging visualization
- **DebugVisualFeedback**: Element highlighting and tap indicators
- **VoiceWaveAnimation**: Voice activity visualization

#### Debug Mode
- Visual element highlighting (green for clickable, yellow for text)
- Tap location indicators (red circles)
- Screen state change flash (white border)
- Debug overlay with step tracking and reasoning display

#### Developer Experience
- Comprehensive TypeScript type definitions
- Example Expo application with 5 demo scenarios
- Full API documentation and usage guides
- Jest test suite with unit and integration tests

#### Documentation
- Complete README with installation and quick start
- API reference documentation
- Usage guides for common scenarios
- Debug mode documentation
- Expo plugin setup guide

### Technical Details

#### Dependencies
- `@google/generative-ai`: ^0.24.1 - Gemini API client
- `fast-xml-parser`: ^5.3.2 - XML parsing for UI hierarchy
- `react-native-fs`: ^2.20.0 - File system operations
- `react-native-tts`: ^4.1.1 - Text-to-speech
- `react-native-voice`: ^0.3.0 - Speech-to-text

#### Peer Dependencies
- `expo`: * - Expo framework
- `react`: * - React library
- `react-native`: * - React Native framework

#### Build System
- TypeScript compilation with declaration files
- Expo module scripts for building and bundling
- Native Android code in Kotlin
- iOS placeholder implementation (Swift)

### Known Limitations
- Android only (iOS support planned for future release)
- Requires Accessibility Service permissions
- Gemini API key required for LLM functionality
- Maximum 100 steps per task execution (configurable)

### Breaking Changes
None - Initial release

---

## [0.2.0] - 2024-11-14

### Added

#### Trigger System
- **Scheduled Triggers**: Execute tasks at specific times with day-of-week selection
- **Notification Triggers**: React to app notifications with pattern matching
- **Boot Persistence**: Triggers survive device reboots via BootReceiver
- **Priority System**: Control execution order when multiple triggers fire
- **Execution History**: Track trigger performance and results
- **TriggerManager**: Complete CRUD operations for trigger management
- **useTriggers Hook**: React hook for trigger management with state tracking
- **Native Android Integration**: AlarmManager for scheduling, NotificationListenerService for notifications
- **Comprehensive Documentation**: Full trigger system guide with examples

#### Clarification System
- **Ambiguity Detection**: Analyze instructions for unclear parameters
- **Interactive Dialogue**: Multi-turn conversation for clarification
- **Instruction Enhancement**: Merge clarifications into original instructions
- **ClarificationAgent**: LLM-powered ambiguity analysis
- **DialogueManager**: State management for multi-turn dialogues
- **InstructionEnhancer**: Merge clarification responses into instructions
- **useClarification Hook**: React hook for clarification workflows
- **Configurable Sensitivity**: Adjust ambiguity detection threshold

#### Dynamic Tools
- **Tool Generation**: Create custom tools at runtime
- **Tool Registry**: Register and manage custom tools
- **MCP Tool Adapter**: Model Context Protocol integration
- **DynamicToolGenerator**: Generate tools from descriptions
- **Action Retry Handler**: Automatic retry logic for failed actions

#### Additional Actions
- **take_screenshot**: Capture screen screenshots
- **get_clipboard**: Read clipboard content
- **set_clipboard**: Write to clipboard
- **get_installed_apps**: List installed applications
- **get_current_app**: Get current app package name
- **send_notification**: Send system notifications
- **list_files**: List files in workspace
- **delete_file**: Delete files from workspace
- **generate_tool**: Generate custom tools dynamically
- **execute_dynamic_tool**: Execute generated tools

#### Documentation
- **Trigger System Guide**: Complete guide with setup, examples, and best practices
- **Updated README**: Added trigger system and clarification features
- **API Documentation**: Updated with new types and interfaces
- **Usage Examples**: Comprehensive examples for all new features

### Changed
- Updated package version to 0.2.0
- Enhanced main index exports with trigger and clarification systems
- Improved TypeScript type safety across all modules

### Fixed
- TypeScript compilation errors in trigger system
- Unused import warnings in test files
- Type casting issues in TriggerManager

---

## [Unreleased]

### Planned Features
- iOS support with native Accessibility APIs
- Wake Word Detection with Porcupine integration
- Speech Coordinator for TTS/STT conflict prevention
- Multi-agent support for concurrent task execution
- Streaming LLM responses for faster feedback
- Vision integration for screenshot analysis
- Session recording and replay
- Cloud sync for file system
- Performance analytics and metrics

---

[0.1.0]: https://github.com/qubasehq/que-mobile-sdk/releases/tag/v0.
