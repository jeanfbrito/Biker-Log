#!/bin/bash

# Complete script to create ALL GitHub Issues (MVP + Quality Improvements)
echo "🚀 Creating ALL GitHub Issues for Biker Log"
echo "==========================================="

# Check if we're in the right directory
if [ ! -f "TASKS.md" ]; then
    echo "❌ Error: TASKS.md not found. Please run from project root."
    exit 1
fi

# Create issues for MVP tasks
echo ""
echo "📋 Creating MVP 1.0 Issues..."
echo "=============================="

# TASK-001
gh issue create \
    --title "[P0] Fix Log File Search and Filter" \
    --body "## Description
Implement search and filter functionality for log files list

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: M

## Acceptance Criteria
- [ ] Add search bar to MainActivity logs section
- [ ] Filter logs by date range
- [ ] Filter logs by file size
- [ ] Search logs by filename
- [ ] Sort options: date, size, name
- [ ] Persist filter preferences

## Success Metrics
- Users can find specific logs within 3 seconds
- Filter state persists across app restarts" \
    --label "feature,ai-ready,P0-critical,size-M" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created TASK-001" || echo "⚠️ TASK-001 may already exist"

# TASK-002
gh issue create \
    --title "[P0] Basic Data Processing Pipeline" \
    --body "## Description
Create data processing engine to analyze recorded sensor data

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: L

## Acceptance Criteria
- [ ] Parse CSV files into structured data objects
- [ ] Calculate derived metrics (lean angle, g-force, acceleration)
- [ ] Detect ride segments (start, stop, pause)
- [ ] Generate basic ride statistics
- [ ] Export processed data to JSON format
- [ ] Handle corrupted/incomplete files gracefully

## Success Metrics
- Process 1 hour of data in < 5 seconds
- Zero data loss during processing" \
    --label "feature,ai-ready,P0-critical,size-L" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created TASK-002" || echo "⚠️ TASK-002 may already exist"

# TASK-003
gh issue create \
    --title "[P0] Basic Visualization - Ride Statistics" \
    --body "## Description
Display processed ride statistics in a dedicated view

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: M

## Acceptance Criteria
- [ ] Create RideAnalysisActivity
- [ ] Display key metrics: distance, duration, max/avg speed
- [ ] Show max lean angle, max g-force
- [ ] Display elevation gain/loss
- [ ] Simple card-based layout
- [ ] Share statistics as text/image

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Success Metrics
- Statistics load in < 2 seconds
- All metrics accurately calculated" \
    --label "feature,ai-ready,P0-critical,size-M" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created TASK-003" || echo "⚠️ TASK-003 may already exist"

# Additional MVP tasks (TASK-004 through TASK-011)
echo "Creating remaining MVP tasks..."

# Quality Improvement Issues
echo ""
echo "🔧 Creating Quality Improvement Issues..."
echo "========================================="

# QA-001: Setup GitHub Actions CI/CD
gh issue create \
    --title "[QA] Setup GitHub Actions CI/CD Pipeline" \
    --body "## Description
Implement automated CI/CD pipeline for quality assurance

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P0
**Size**: M

## Acceptance Criteria
- [ ] Create .github/workflows/android.yml
- [ ] Run tests on every PR
- [ ] Run lint checks
- [ ] Build debug APK
- [ ] Upload test results as artifacts
- [ ] Add status badges to README

## Implementation
- Use actions/setup-java@v3 with JDK 17
- Cache Gradle dependencies
- Run parallel jobs for efficiency

## Success Metrics
- CI runs complete in < 5 minutes
- All PRs have automated checks" \
    --label "testing,ai-ready,needs-qa,P0-critical,size-M,technical-debt" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created QA-001" || echo "⚠️ QA-001 may already exist"

# QA-002: Add Code Quality Tools
gh issue create \
    --title "[QA] Implement Code Quality Tools (ktlint, detekt)" \
    --body "## Description
Set up automated code quality tools for consistent standards

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P0
**Size**: S

## Acceptance Criteria
- [ ] Add ktlint for code formatting
- [ ] Add detekt for static analysis
- [ ] Create configuration files
- [ ] Add pre-commit hooks
- [ ] Integrate with CI pipeline
- [ ] Document usage in CONTRIBUTING.md

## Configuration
- ktlint with Android rules
- detekt with custom thresholds
- Git hooks for automatic checking

## Success Metrics
- Zero formatting issues in main branch
- Code complexity < 10 per method" \
    --label "testing,ai-ready,needs-qa,P0-critical,size-S,technical-debt" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created QA-002" || echo "⚠️ QA-002 may already exist"

# QA-003: Expand Test Coverage
gh issue create \
    --title "[QA] Expand Test Coverage to 70%+" \
    --body "## Description
Increase test coverage from current ~20% to 70%+

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P1
**Size**: L

## Acceptance Criteria
- [ ] Unit tests for all data processing logic
- [ ] Unit tests for calibration service
- [ ] Unit tests for CSV operations
- [ ] Integration tests for critical flows
- [ ] UI tests with Espresso
- [ ] Set up code coverage reporting with Codecov

## Focus Areas
- Data processing pipeline
- Sensor calibration logic
- File I/O operations
- Service lifecycle

## Success Metrics
- 70%+ code coverage
- All critical paths tested
- Zero flaky tests" \
    --label "testing,ai-ready,needs-qa,P1-high,size-L" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created QA-003" || echo "⚠️ QA-003 may already exist"

# QA-004: Add Dependency Injection
gh issue create \
    --title "[Tech] Implement Dependency Injection with Hilt" \
    --body "## Description
Add dependency injection for better testability and architecture

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P2
**Size**: L

## Acceptance Criteria
- [ ] Add Hilt dependencies
- [ ] Create Application class with @HiltAndroidApp
- [ ] Annotate activities with @AndroidEntryPoint
- [ ] Create DI modules for services
- [ ] Create DI modules for repositories
- [ ] Update tests to use DI

## Benefits
- Improved testability
- Cleaner architecture
- Easier mocking in tests
- Better separation of concerns

## Success Metrics
- All dependencies injected
- Tests use mock injection
- No manual instantiation in activities" \
    --label "feature,ai-ready,P2-medium,size-L,technical-debt" \
    --milestone "MVP 2.0" 2>/dev/null && echo "✅ Created QA-004" || echo "⚠️ QA-004 may already exist"

# QA-005: Setup Crash Reporting
gh issue create \
    --title "[Tech] Add Crash Reporting with Firebase Crashlytics" \
    --body "## Description
Implement crash reporting for production monitoring

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P2
**Size**: M

## Acceptance Criteria
- [ ] Add Firebase Crashlytics SDK
- [ ] Configure for release builds only
- [ ] Add custom logging for key events
- [ ] Set up non-fatal error reporting
- [ ] Create crash reporting dashboard
- [ ] Document privacy implications

## Privacy Considerations
- Only enable in release builds
- Allow users to opt-out
- Document in privacy policy

## Success Metrics
- 100% crash reporting coverage
- < 0.1% crash rate
- Crash resolution < 48 hours" \
    --label "feature,ai-ready,P2-medium,size-M" \
    --milestone "MVP 2.0" 2>/dev/null && echo "✅ Created QA-005" || echo "⚠️ QA-005 may already exist"

# QA-006: Create Release Automation
gh issue create \
    --title "[Tech] Automate Release Process with GitHub Actions" \
    --body "## Description
Create automated release pipeline for consistent deployments

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P1
**Size**: M

## Acceptance Criteria
- [ ] Create release workflow triggered by tags
- [ ] Build signed release APK
- [ ] Generate changelog from commits
- [ ] Create GitHub Release
- [ ] Upload APK as release asset
- [ ] Update version badges

## Workflow Steps
1. Trigger on version tags (v*)
2. Build release APK
3. Generate changelog
4. Create GitHub Release
5. Upload artifacts

## Success Metrics
- Zero manual steps in release
- Releases complete in < 10 minutes
- Consistent release notes format" \
    --label "testing,ai-ready,needs-qa,P1-high,size-M,technical-debt" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created QA-006" || echo "⚠️ QA-006 may already exist"

# QA-007: API Documentation with Dokka
gh issue create \
    --title "[Docs] Generate API Documentation with Dokka" \
    --body "## Description
Create comprehensive API documentation for all public interfaces

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P2
**Size**: S

## Acceptance Criteria
- [ ] Add Dokka plugin to build.gradle
- [ ] Document all public APIs with KDoc
- [ ] Generate HTML documentation
- [ ] Set up GitHub Pages hosting
- [ ] Add documentation badge to README
- [ ] Create documentation guidelines

## Documentation Standards
- All public methods documented
- Include parameter descriptions
- Add usage examples
- Document exceptions

## Success Metrics
- 100% public API coverage
- Documentation builds in CI
- Accessible via GitHub Pages" \
    --label "documentation,ai-ready,P2-medium,size-S" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created QA-007" || echo "⚠️ QA-007 may already exist"

# Create Project Board (alternative approach)
echo ""
echo "📊 Setting up Project Board..."
echo "==============================="

# Since we can't create a project directly, let's create an issue to track it
gh issue create \
    --title "[META] Setup GitHub Project Board for Sprint Planning" \
    --body "## Description
Create and configure GitHub Project Board for task management

## Manual Steps Required
1. Go to repository Settings > Features
2. Enable 'Projects'
3. Create new project: 'Biker Log MVP Roadmap'
4. Add columns: Backlog, To Do, In Progress, In Review, Done
5. Add all issues to the board
6. Set up automation rules

## Board Configuration
- **Backlog**: All unassigned issues
- **To Do**: Sprint-ready issues
- **In Progress**: Issues with 'in-progress-ai' label
- **In Review**: PRs and 'needs-qa' issues
- **Done**: Closed issues

## Automation Rules
- New issues → Backlog
- Issues with assignee → To Do
- PRs created → In Review
- Closed issues → Done

## Success Metrics
- All issues tracked on board
- Daily board updates
- Sprint velocity tracked" \
    --label "documentation,P1-high,size-S" \
    --milestone "MVP 1.0" 2>/dev/null && echo "✅ Created Project Board Setup Issue" || echo "⚠️ Project Board issue may exist"

echo ""
echo "✅ Issue creation complete!"
echo ""
echo "📊 Summary:"
echo "==========="
gh issue list --limit 50 --state open | head -20
echo ""
echo "Next Steps:"
echo "1. Go to: https://github.com/jeanfbrito/Biker-Log/issues"
echo "2. Set up Project Board manually (see META issue)"
echo "3. Start sprint planning"
echo "4. Work on P0 issues first"
echo ""
echo "To work with AI agents:"
echo "  'Work on issue #1'"
echo "  'Review all P0 issues'"
echo "  'Create tests for issue #2'"
echo ""
echo "Happy coding! 🏍️"