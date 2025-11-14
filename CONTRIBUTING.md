# Contributing to QUE Mobile SDK

Thank you for your interest in contributing to QUE Mobile SDK! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Development Workflow](#development-workflow)
- [Testing](#testing)
- [Submitting Changes](#submitting-changes)
- [Coding Standards](#coding-standards)
- [Documentation](#documentation)
- [Community](#community)

## Code of Conduct

We are committed to providing a welcoming and inclusive environment for all contributors. Please be respectful and constructive in all interactions.

### Our Standards

- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

## Getting Started

### Prerequisites

- Node.js 18+ and npm
- Android Studio with Android SDK
- Expo CLI (`npm install -g expo-cli`)
- Git
- A Gemini API key for testing

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/que-mobile-sdk.git
   cd que-mobile-sdk
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/qubasehq/que-mobile-sdk.git
   ```

## Development Setup

### Install Dependencies

```bash
npm install
```

### Build the Package

```bash
npm run build
```

### Run Tests

```bash
npm test
```

### Run Example App

```bash
cd example
npm install
npx expo start
```

## Project Structure

```
que-mobile-sdk/
├── src/                    # TypeScript source code
│   ├── core/              # Agent core loop
│   ├── actions/           # Action executor and types
│   ├── perception/        # Screen analysis
│   ├── memory/            # Memory and file system
│   ├── llm/               # Gemini client
│   ├── voice/             # Voice integration
│   ├── hooks/             # React hooks
│   ├── components/        # React components
│   └── types/             # TypeScript types
├── android/               # Native Android code (Kotlin)
├── ios/                   # Native iOS code (Swift)
├── example/               # Example Expo app
├── docs/                  # Documentation
└── __tests__/            # Test files
```

## Development Workflow

### Creating a Branch

Create a feature branch from `main`:

```bash
git checkout -b feature/your-feature-name
```

Branch naming conventions:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring
- `test/` - Test additions or updates

### Making Changes

1. Make your changes in the appropriate files
2. Follow the [Coding Standards](#coding-standards)
3. Add or update tests as needed
4. Update documentation if required
5. Build and test locally:
   ```bash
   npm run build
   npm test
   ```

### Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Test additions or updates
- `chore`: Build process or auxiliary tool changes

Examples:
```
feat(actions): add screenshot capture action

Implement new screenshot action that captures the current screen
and saves it to the file system.

Closes #123
```

```
fix(perception): handle empty UI hierarchy

Fix crash when UI hierarchy is empty by adding null check
and returning empty screen state.
```

## Testing

### Running Tests

```bash
# Run all tests
npm test

# Run tests in watch mode
npm test -- --watch

# Run specific test file
npm test -- SemanticParser.test.ts

# Run with coverage
npm test -- --coverage
```

### Writing Tests

- Place test files in `__tests__` directories next to the code they test
- Use descriptive test names that explain what is being tested
- Follow the Arrange-Act-Assert pattern
- Mock external dependencies (native modules, API calls)
- Test both success and error cases

Example:
```typescript
describe('SemanticParser', () => {
  it('should parse XML and extract elements', () => {
    // Arrange
    const xml = '<node text="Button" clickable="true" />'
    const parser = new SemanticParser()
    
    // Act
    const elements = parser.parse(xml)
    
    // Assert
    expect(elements).toHaveLength(1)
    expect(elements[0].text).toBe('Button')
    expect(elements[0].isClickable).toBe(true)
  })
})
```

## Submitting Changes

### Pull Request Process

1. Update your branch with the latest upstream changes:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. Push your changes to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

3. Create a Pull Request on GitHub with:
   - Clear title describing the change
   - Detailed description of what changed and why
   - Reference to related issues (e.g., "Closes #123")
   - Screenshots or videos for UI changes
   - Test results

4. Address review feedback:
   - Make requested changes
   - Push updates to the same branch
   - Respond to comments

5. Once approved, a maintainer will merge your PR

### Pull Request Checklist

- [ ] Code follows the project's coding standards
- [ ] Tests pass locally (`npm test`)
- [ ] New tests added for new functionality
- [ ] Documentation updated if needed
- [ ] Commit messages follow conventional commits
- [ ] No merge conflicts with main branch
- [ ] PR description is clear and complete

## Coding Standards

### TypeScript

- Use TypeScript for all new code
- Enable strict mode in tsconfig.json
- Avoid `any` types - use proper typing
- Use discriminated unions for action types
- Export types alongside implementations

### Code Style

- Use 2 spaces for indentation
- Use single quotes for strings
- Add semicolons at end of statements
- Use trailing commas in multi-line objects/arrays
- Maximum line length: 100 characters

### Naming Conventions

- **Classes**: PascalCase (e.g., `ActionExecutor`)
- **Interfaces**: PascalCase (e.g., `AgentConfig`)
- **Functions**: camelCase (e.g., `executeAction`)
- **Variables**: camelCase (e.g., `screenState`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_STEPS`)
- **Files**: PascalCase for classes, camelCase for utilities

### Comments

- Use JSDoc comments for public APIs
- Explain "why" not "what" in comments
- Keep comments up to date with code changes
- Remove commented-out code

Example:
```typescript
/**
 * Executes an action on the device via Accessibility Service.
 * 
 * @param action - The action to execute
 * @param screenState - Current screen state with element map
 * @returns Result of the action execution
 * @throws {QueError} If action execution fails
 */
async execute(action: Action, screenState: ScreenState): Promise<ActionResult>
```

### Error Handling

- Use custom error classes (e.g., `QueError`)
- Include error categories and recovery info
- Provide helpful error messages
- Log errors appropriately

## Documentation

### Code Documentation

- Add JSDoc comments to all public APIs
- Include parameter descriptions and return types
- Document exceptions that can be thrown
- Provide usage examples in comments

### User Documentation

Update documentation in `docs/` when:
- Adding new features
- Changing existing APIs
- Adding configuration options
- Fixing bugs that affect usage

Documentation files:
- `README.md` - Main documentation
- `docs/API.md` - API reference
- `docs/GUIDES.md` - Usage guides
- `docs/DEBUG_MODE.md` - Debug mode documentation
- `docs/EXPO_PLUGIN.md` - Expo plugin setup

### Example Updates

When adding new features, update the example app:
- Add new screen demonstrating the feature
- Update navigation to include new screen
- Add comments explaining the implementation

## Community

### Getting Help

- **GitHub Issues**: Report bugs or request features
- **GitHub Discussions**: Ask questions or discuss ideas
- **Pull Requests**: Contribute code or documentation

### Reporting Bugs

When reporting bugs, include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, React Native version, etc.)
- Error messages or logs
- Screenshots or videos if applicable

Use the bug report template when creating issues.

### Requesting Features

When requesting features, include:
- Clear description of the feature
- Use case and motivation
- Proposed API or implementation (if applicable)
- Examples of similar features in other projects

Use the feature request template when creating issues.

### Code Review

All contributions go through code review:
- Be open to feedback and suggestions
- Respond to comments in a timely manner
- Ask questions if something is unclear
- Be respectful and constructive

## Release Process

Releases are managed by maintainers:

1. Update version in `package.json`
2. Update `CHANGELOG.md` with release notes
3. Create git tag: `git tag v0.1.0`
4. Push tag: `git push origin v0.1.0`
5. Publish to npm: `npm publish`
6. Create GitHub release with notes

## License

By contributing to QUE Mobile SDK, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to QUE Mobile SDK! Your efforts help make mobile automation accessible to developers everywhere.
