# Contributing to Moto Sensor Logger

Thank you for your interest in contributing to Moto Sensor Logger! This guide will help you get started with contributing to the project.

## 📋 Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Code Quality Standards](#code-quality-standards)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)

## Code of Conduct

Please be respectful and constructive in all interactions. We aim to maintain a welcoming and inclusive environment for all contributors.

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally
3. Create a new branch for your feature or bug fix
4. Make your changes
5. Push to your fork and submit a pull request

## Development Setup

### Requirements
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK with API level 30+
- Gradle 8.11.1+

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Building APK
```bash
./gradlew assembleDebug
```

## Code Quality Standards

We maintain high code quality standards using automated tools. All pull requests must pass these checks.

### Kotlin Code Style (ktlint)

We use [ktlint](https://github.com/pinterest/ktlint) to enforce Kotlin code style.

#### Check Code Style
```bash
./gradlew ktlintCheck
```

#### Auto-format Code
```bash
./gradlew ktlintFormat
```

#### Key Style Rules
- Android Kotlin style guide
- Maximum line length: 120 characters
- No wildcard imports (except java.util.*)
- Consistent naming conventions

### Static Analysis (Detekt)

We use [Detekt](https://detekt.github.io/) for static code analysis.

#### Run Analysis
```bash
./gradlew detekt
```

#### Configuration
See `config/detekt/detekt.yml` for our custom rules.

#### Key Metrics
- Maximum method complexity: 15
- Maximum method length: 60 lines
- Maximum class size: 600 lines
- Maximum parameters: 6 (functions), 7 (constructors)

### Android Lint

#### Run Lint
```bash
./gradlew lint
```

Lint reports are generated in `app/build/reports/lint-results-debug.html`

### Pre-commit Hooks (Optional)

To automatically check code quality before committing:

```bash
# Install pre-commit hook
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/sh
echo "Running ktlint..."
./gradlew ktlintCheck --daemon
if [ $? -ne 0 ]; then
    echo "ktlint failed! Run './gradlew ktlintFormat' to fix issues."
    exit 1
fi

echo "Running detekt..."
./gradlew detekt --daemon
if [ $? -ne 0 ]; then
    echo "Detekt found issues! Check the report for details."
    exit 1
fi

echo "All checks passed!"
EOF

chmod +x .git/hooks/pre-commit
```

## Submitting Changes

### Pull Request Process

1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Write Clear Commit Messages**
   - Use conventional commit format:
     - `feat:` for new features
     - `fix:` for bug fixes
     - `docs:` for documentation
     - `style:` for formatting changes
     - `refactor:` for code refactoring
     - `test:` for test additions/changes
     - `chore:` for maintenance tasks

3. **Ensure All Checks Pass**
   ```bash
   ./gradlew ktlintCheck
   ./gradlew detekt
   ./gradlew lint
   ./gradlew test
   ```

4. **Update Documentation**
   - Update README.md if needed
   - Add/update code comments
   - Update CHANGELOG.md for significant changes

5. **Submit Pull Request**
   - Provide a clear description of changes
   - Reference any related issues
   - Include screenshots for UI changes
   - Ensure all CI checks pass

### PR Review Criteria

Your PR will be reviewed for:
- Code quality and style compliance
- Test coverage (when tests are re-enabled)
- Performance impact
- Security considerations
- Documentation completeness

## Reporting Issues

### Bug Reports

When reporting bugs, please include:
- Device model and Android version
- Steps to reproduce
- Expected behavior
- Actual behavior
- Logs (if applicable)
- Screenshots (if UI-related)

### Feature Requests

For feature requests, please describe:
- The problem you're trying to solve
- Your proposed solution
- Alternative solutions considered
- Any implementation details

## CI/CD Pipeline

All pull requests trigger our automated pipeline:

1. **Code Quality Checks**
   - ktlint validation
   - Detekt analysis
   - Android lint

2. **Build Verification**
   - Debug APK build
   - Release APK build

3. **Security Scanning**
   - Dependency vulnerability checks
   - Secret detection
   - CodeQL analysis

## Development Tips

### Performance Considerations
- Optimize sensor sampling rates (see `SensorSamplingRates.kt`)
- Minimize battery usage
- Efficient CSV writing

### Testing on Real Devices
- Test on actual motorcycles when possible
- Verify sensor calibration
- Check GPS accuracy in various conditions

### Debugging
- Use Android Studio's profiler for performance analysis
- Monitor battery consumption
- Check logcat for sensor data issues

## Questions?

If you have questions, feel free to:
- Open a discussion on GitHub
- Check existing issues for similar questions
- Review the project documentation

Thank you for contributing to Moto Sensor Logger! 🏍️