# Contributing to Moto Sensor Logger

🎉 First off, thank you for considering contributing to Moto Sensor Logger! 🎉

The following is a set of guidelines for contributing to Moto Sensor Logger. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [I don't want to read this whole thing, I just have a question!](#i-dont-want-to-read-this-whole-thing-i-just-have-a-question)
3. [What should I know before I get started?](#what-should-i-know-before-i-get-started)
4. [How Can I Contribute?](#how-can-i-contribute)
5. [Styleguides](#styleguides)
6. [Additional Notes](#additional-notes)

## Code of Conduct

This project and everyone participating in it is governed by the [Moto Sensor Logger Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## I don't want to read this whole thing, I just have a question!

> **Note:** Please don't file an issue to ask a question. You'll get faster results by using the resources below.

* If you have a usage question, check the [README](README.md) first
* For technical questions about sensor data or calibration, see our [Wiki](../../wiki)
* For general discussion, use the [Discussions](../../discussions) tab

## What should I know before I get started?

### Project Overview

Moto Sensor Logger is a specialized Android application for recording motorcycle telemetry data using device sensors. The app focuses on:

* High-frequency sensor data collection (50-100Hz)
* Real-time calibration for motorcycle mounting positions
* Efficient CSV data logging with embedded metadata
* Live telemetry visualization

### Project Structure

```
app/
├── src/main/java/com/motosensorlogger/
│   ├── calibration/     # Sensor calibration logic
│   ├── data/            # Data models and CSV handling
│   ├── services/        # Background services
│   └── views/           # Custom UI components
├── src/test/            # Unit tests
└── src/androidTest/     # Instrumentation tests
```

### Design Decisions

Before contributing, understand these key design principles:

1. **Performance First**: The app handles high-frequency sensor data. Any contribution must maintain performance.
2. **Data Integrity**: CSV logs are critical. Never compromise data accuracy or format consistency.
3. **Battery Efficiency**: Background services must be optimized for battery life.
4. **User Safety**: The app is used while riding. UI must be glanceable and non-distracting.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

#### How Do I Submit A Bug Report?

Bugs are tracked as [GitHub issues](../../issues). Create an issue and provide the following information:

* **Use a clear and descriptive title**
* **Describe the exact steps which reproduce the problem**
* **Provide specific examples to demonstrate the steps**
* **Describe the behavior you observed after following the steps**
* **Explain which behavior you expected to see instead and why**
* **Include logs** from `adb logcat` if applicable
* **Include device information** (model, Android version, app version)

#### Template for Bug Reports

```markdown
**Device Information:**
- Device Model: [e.g., Pixel 6]
- Android Version: [e.g., Android 13]
- App Version: [e.g., 1.1.0]

**Description:**
[Clear description of the bug]

**Steps to Reproduce:**
1. [First Step]
2. [Second Step]
3. [and so on...]

**Expected Behavior:**
[What you expected to happen]

**Actual Behavior:**
[What actually happened]

**Logs:**
```
[Paste any relevant logs here]
```

**Additional Context:**
[Add any other context about the problem here]
```

### Suggesting Enhancements

Enhancement suggestions are tracked as [GitHub issues](../../issues). Before creating enhancement suggestions, please check the [TASKS.md](TASKS.md) file to see if it's already planned.

#### How Do I Submit An Enhancement Suggestion?

* **Use a clear and descriptive title**
* **Provide a step-by-step description of the suggested enhancement**
* **Provide specific examples to demonstrate the steps**
* **Describe the current behavior** and **explain which behavior you expected to see instead**
* **Explain why this enhancement would be useful**
* **List some other applications where this enhancement exists** (if applicable)

### Your First Code Contribution

Unsure where to begin? You can start by looking through these `beginner` and `help-wanted` issues:

* [Beginner issues](../../labels/good%20first%20issue) - issues which should only require a few lines of code
* [Help wanted issues](../../labels/help%20wanted) - issues which should be a bit more involved

### Pull Requests

The process described here has several goals:

- Maintain code quality
- Fix problems that are important to users
- Engage the community in working toward the best possible app
- Enable a sustainable system for maintainers to review contributions

Please follow these steps:

1. **Fork the repo and create your branch from `main`**
   ```bash
   git checkout -b feature/my-new-feature
   ```

2. **Set up your development environment**
   ```bash
   ./install.sh  # Automated setup script
   ```

3. **Make your changes**
   - Follow the [Kotlin Style Guide](#kotlin-style-guide)
   - Add tests for new functionality
   - Update documentation as needed

4. **Ensure the test suite passes**
   ```bash
   ./gradlew test
   ```

5. **Run code quality checks**
   ```bash
   ./gradlew lint
   ./gradlew ktlintCheck
   ```

6. **Commit your changes**
   ```bash
   git commit -m "Add feature: brief description"
   ```
   
   Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` New feature
   - `fix:` Bug fix
   - `docs:` Documentation only
   - `style:` Formatting, missing semicolons, etc
   - `refactor:` Code change that neither fixes a bug nor adds a feature
   - `perf:` Performance improvement
   - `test:` Adding missing tests
   - `chore:` Changes to build process or auxiliary tools

7. **Push to your fork and submit a pull request**

8. **Fill out the PR template** with:
   - Description of changes
   - Related issue numbers
   - Screenshots (for UI changes)
   - Test results

#### Pull Request Checklist

- [ ] My code follows the code style of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published

## Styleguides

### Git Commit Messages

* Use the present tense ("Add feature" not "Added feature")
* Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
* Limit the first line to 72 characters or less
* Reference issues and pull requests liberally after the first line

### Kotlin Style Guide

We follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

```kotlin
// Use meaningful variable names
val sensorFrequency = 100 // Good
val sf = 100 // Bad

// Prefer immutability
val items = mutableListOf<String>() // When mutation is needed
val items = listOf<String>() // Default to immutable

// Use explicit types for public APIs
fun calculateCalibration(data: SensorData): CalibrationResult // Good
fun calculateCalibration(data) // Bad

// Document public functions
/**
 * Calculates calibration parameters from sensor data
 * @param data Raw sensor data samples
 * @return Calibration result with quality metrics
 */
fun calculateCalibration(data: SensorData): CalibrationResult
```

### Documentation Style Guide

* Use [KDoc](https://kotlinlang.org/docs/kotlin-doc.html) for API documentation
* Include examples for complex functionality
* Keep documentation close to code
* Update documentation with code changes

### Testing Style Guide

```kotlin
class ExampleTest {
    @Test
    fun `should describe expected behavior when condition is met`() {
        // Given - setup
        val input = TestData()
        
        // When - action
        val result = systemUnderTest.process(input)
        
        // Then - verification
        assertEquals(expected, result)
    }
}
```

## Additional Notes

### Issue and Pull Request Labels

| Label | Description |
|-------|-------------|
| `bug` | Something isn't working |
| `enhancement` | New feature or request |
| `good first issue` | Good for newcomers |
| `help wanted` | Extra attention is needed |
| `invalid` | This doesn't seem right |
| `question` | Further information is requested |
| `wontfix` | This will not be worked on |
| `documentation` | Improvements or additions to documentation |
| `performance` | Performance improvements |
| `testing` | Test improvements or additions |

### Recognition

Contributors who submit accepted PRs will be added to the [Contributors](../../graphs/contributors) list and mentioned in release notes.

### Getting Help

If you need help, you can:

1. Check the [README](README.md) and [Wiki](../../wiki)
2. Look through existing [issues](../../issues)
3. Open a [discussion](../../discussions)
4. Contact the maintainers

## Thank You!

Your contributions to open source, no matter how small, help build better software for everyone. Thank you for taking the time to contribute!

---

*This contributing guide is adapted from the [Atom contributing guide](https://github.com/atom/atom/blob/master/CONTRIBUTING.md) and best practices from the open-source community.*