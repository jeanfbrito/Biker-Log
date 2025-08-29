# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Auto-approved Commands for Worktree Operations

The following commands are pre-approved for parallel worktree operations:
- `cd .git/worktrees/*`
- `cd /Users/jean/Github/Biker Log/.git/worktrees/*`
- Any git worktree commands
- Build and test commands in worktrees

## IMPORTANT: Automatic Worktree Setup for Parallel Work

**Every new agent session MUST work in its own git worktree to prevent conflicts.**

Before starting any work, run:
```bash
# This creates an isolated workspace for this agent session
source ./scripts/agent-auto-setup.sh
```

This ensures:
- Each agent works in an isolated branch
- No conflicts between parallel agents
- Clean merge workflow
- Automatic tracking of agent work

To clean up after work is done:
```bash
# Replace AGENT_ID with the actual agent ID
./scripts/agent-cleanup.sh <AGENT_ID>
```

## Build and Development Commands

### Building the App
```bash
# Install with auto-detected Java (macOS with Homebrew)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Install on connected device
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew installDebug

# Quick install script (handles Java setup automatically)
./install.sh

# Complete setup with prompts (for first-time setup)
./setup.sh
```

### Running Tests
```bash
# Run all unit tests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test

# Run all tests with detailed output
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --info

# Clean and re-run tests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew cleanTest test

# Run specific test class
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests "*CsvLoggerTest"
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests "*CalibrationServiceTest"
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests "*CsvHeaderIntegrityTest"

# Run multiple specific tests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest --tests "*CalibrationServiceTest*" --tests "*CsvLoggerTest*"
```

### Linting and Code Quality
```bash
# IMPORTANT: Always run lint locally before committing!
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew lint

# Full build with all checks
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build

# Quick lint check (faster)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew lintDebug
```

**Pre-commit checklist:**
1. ✅ Run lint: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew lint`
2. ✅ Run tests: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
3. ✅ Verify build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug`

## Pull Request Guidelines

**PR Title Format:** Must follow conventional commit standards with type prefix:
- `feat: Description` - New features
- `fix: Description` - Bug fixes  
- `docs: Description` - Documentation changes
- `refactor: Description` - Code refactoring
- `test: Description` - Test additions/changes
- `chore: Description` - Maintenance tasks

**Example:** `feat: Log File Search and Filter Functionality [Issue #1]`

**PR Validation:** The CI pipeline validates PR titles - they MUST include a type prefix or the validation will fail.

## Architecture Overview

### Core Components

**MainActivity** (`app/src/main/java/com/motosensorlogger/MainActivity.kt`)
- Entry point and main UI controller
- Handles permission requests, service binding, and UI state management
- Manages calibration UI flow and failure recovery
- Monitors sensor status in real-time when expanded

**SensorLoggerService** (`app/src/main/java/com/motosensorlogger/services/SensorLoggerService.kt`)
- Foreground service for continuous sensor data collection
- Manages calibration flow → recording state transition
- Critical: Ensures calibration state is never mixed with recording data
- Handles IMU (100Hz), Magnetometer (25Hz), Barometer (25Hz), GPS (5Hz) sampling
- Uses coroutines for efficient async operations

**CsvLogger** (`app/src/main/java/com/motosensorlogger/data/CsvLogger.kt`)
- Event-based sparse CSV format for efficient storage
- Self-contained files with embedded schema in header
- Critical: Header must be written exactly once with consistent calibration state
- Uses coroutine channels for non-blocking I/O with 32KB buffer

**CalibrationService** (`app/src/main/java/com/motosensorlogger/calibration/CalibrationService.kt`)
- Manages 3-second calibration process with quality monitoring
- Provides real-time progress updates via StateFlow
- Adaptive duration (extends if quality is poor)
- Quality levels: EXCELLENT, GOOD, POOR, BAD

### Critical Data Integrity Rules

1. **CSV Header Integrity**: The CSV header must NEVER be duplicated and calibration state must be consistent throughout the file
2. **Calibration State**: A file is either fully calibrated (with reference pitch/roll) OR uncalibrated - never mixed
3. **Service State Synchronization**: UI must observe service state reactively via StateFlow to prevent state mismatches

### Data Flow

1. **Calibration Flow**:
   - User starts recording → Service enters CALIBRATING state
   - CalibrationService collects samples for 3+ seconds
   - On success → Writes calibrated header → Starts recording
   - On failure → Shows dialog with Retry/Skip/Settings options
   - Skip option → Writes uncalibrated header → Starts recording

2. **Recording Flow**:
   - Raw sensor data logged at configured frequencies
   - Events written to CSV via coroutine channel
   - Batch writing with periodic flushing for data safety

3. **CSV Format**:
   - Self-contained with embedded schema
   - Event-based sparse format (timestamp, sensor_type, data1-6)
   - Calibration data in header if available

## Testing Philosophy

Tests focus on critical data integrity, particularly:
- CSV header consistency (`CsvHeaderIntegrityTest`)
- Calibration state management (`CalibrationServiceTest`)
- Concurrent logging safety (`CsvLoggerTest`)

## Key Dependencies

- Android SDK 34 (compileSdk/targetSdk)
- Min SDK 26 (Android 8.0)
- Kotlin Coroutines for async operations
- Google Play Services Location for GPS
- OpenCSV for CSV operations
- MPAndroidChart for telemetry visualization
- Robolectric for unit testing Android components

## Development Notes

- App uses foreground service with wake lock for continuous operation
- Sensor data is logged raw; calibration transformation applied in post-processing
- UI updates use StateFlow for reactive state management
- Service binding ensures UI-Service synchronization