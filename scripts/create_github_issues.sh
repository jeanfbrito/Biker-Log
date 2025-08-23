#!/bin/bash

# Script to create GitHub Issues from TASKS.md
# This creates all the MVP 1.0 issues with proper labels and milestones

echo "🚀 Creating GitHub Issues for Biker Log MVP"
echo "==========================================="

# Get milestone numbers
MVP1_MILESTONE=$(gh api repos/jeanfbrito/Biker-Log/milestones --jq '.[] | select(.title=="MVP 1.0") | .number')
MVP2_MILESTONE=$(gh api repos/jeanfbrito/Biker-Log/milestones --jq '.[] | select(.title=="MVP 2.0") | .number')

echo "Found milestones: MVP 1.0=#$MVP1_MILESTONE, MVP 2.0=#$MVP2_MILESTONE"
echo ""

# Function to create an issue
create_issue() {
    local title="$1"
    local body="$2"
    local labels="$3"
    local milestone="$4"
    
    echo "Creating: $title"
    gh issue create \
        --title "$title" \
        --body "$body" \
        --label "$labels" \
        --milestone "$milestone" \
        2>/dev/null || echo "  ⚠️ Issue might already exist"
}

# P0 Critical Issues
echo "📍 Creating P0 Critical Issues..."

create_issue \
    "[P0] Fix Log File Search and Filter" \
    "## Description
Implement search and filter functionality for log files list

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: M
**Category**: Core Features

## Acceptance Criteria
- [ ] Add search bar to MainActivity logs section
- [ ] Filter logs by date range
- [ ] Filter logs by file size
- [ ] Search logs by filename
- [ ] Sort options: date, size, name
- [ ] Persist filter preferences

## Success Metrics
- Users can find specific logs within 3 seconds
- Filter state persists across app restarts

## Dependencies
None

## Files to Modify
- \`MainActivity.kt\`
- \`LogFileAdapter.kt\`
- \`activity_main.xml\`" \
    "feature,ai-ready,P0-critical,size-M" \
    "$MVP1_MILESTONE"

create_issue \
    "[P0] Basic Data Processing Pipeline" \
    "## Description
Create data processing engine to analyze recorded sensor data

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: L
**Category**: Data Processing

## Acceptance Criteria
- [ ] Parse CSV files into structured data objects
- [ ] Calculate derived metrics (lean angle, g-force, acceleration)
- [ ] Detect ride segments (start, stop, pause)
- [ ] Generate basic ride statistics (duration, distance, max speed)
- [ ] Export processed data to JSON format
- [ ] Handle corrupted/incomplete files gracefully

## Success Metrics
- Process 1 hour of data in < 5 seconds
- Zero data loss during processing

## Dependencies
None

## Implementation Notes
- Create new package \`com.motosensorlogger.processing\`
- Use coroutines for async processing
- Implement progress callbacks" \
    "feature,ai-ready,P0-critical,size-L" \
    "$MVP1_MILESTONE"

create_issue \
    "[P0] Basic Visualization - Ride Statistics" \
    "## Description
Display processed ride statistics in a dedicated view

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: M
**Category**: UI/UX

## Acceptance Criteria
- [ ] Create RideAnalysisActivity
- [ ] Display key metrics: distance, duration, max/avg speed
- [ ] Show max lean angle, max g-force
- [ ] Display elevation gain/loss
- [ ] Simple card-based layout
- [ ] Share statistics as text/image

## Success Metrics
- Statistics load in < 2 seconds
- All metrics accurately calculated

## Dependencies
- Depends on: Basic Data Processing Pipeline

## UI Requirements
- Material Design 3 cards
- Consistent with existing app theme" \
    "feature,ai-ready,P0-critical,size-M" \
    "$MVP1_MILESTONE"

create_issue \
    "[P0] Graph Visualization - Speed and Lean" \
    "## Description
Implement time-series graphs for critical ride metrics

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: L
**Category**: UI/UX

## Acceptance Criteria
- [ ] Speed over time graph
- [ ] Lean angle over time graph
- [ ] G-force visualization
- [ ] Pinch to zoom functionality
- [ ] Tap to show exact values
- [ ] Sync graphs when scrolling

## Success Metrics
- Smooth rendering at 60fps
- Handle 10,000+ data points

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Technical Notes
- Use MPAndroidChart library (already included)
- Implement data decimation for performance" \
    "feature,ai-ready,P0-critical,size-L" \
    "$MVP1_MILESTONE"

create_issue \
    "[P0] Crazy Moments Detection" \
    "## Description
Automatically detect and highlight interesting ride moments

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P0
**Size**: M
**Category**: Data Processing

## Acceptance Criteria
- [ ] Detect high lean angles (>40°)
- [ ] Detect hard braking events
- [ ] Detect rapid acceleration
- [ ] Detect high g-force moments
- [ ] Create moments list with timestamps
- [ ] Allow threshold customization in settings

## Success Metrics
- 90% accuracy in detecting defined events
- < 10% false positives

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Algorithm Requirements
- Configurable thresholds
- Time-window based detection
- Export moments to JSON" \
    "feature,ai-ready,P0-critical,size-M" \
    "$MVP1_MILESTONE"

# P1 High Priority Issues
echo ""
echo "📍 Creating P1 High Priority Issues..."

create_issue \
    "[P1] Map Integration - Basic Route Display" \
    "## Description
Display ride route on a map using GPS data

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P1
**Size**: L
**Category**: UI/UX

## Acceptance Criteria
- [ ] Integrate OpenStreetMap or Google Maps
- [ ] Plot GPS track on map
- [ ] Show start/end markers
- [ ] Display current position during playback
- [ ] Color code by speed or lean angle
- [ ] Offline map caching for recorded rides

## Success Metrics
- Map loads in < 3 seconds
- Smooth panning and zooming

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Technical Notes
- Prefer OpenStreetMap (free) for MVP
- Use Osmdroid library
- Cache tiles for offline viewing" \
    "feature,ai-ready,P1-high,size-L" \
    "$MVP1_MILESTONE"

create_issue \
    "[P1] Export Ride Data" \
    "## Description
Export processed ride data in standard formats

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P1
**Size**: S
**Category**: Core Features

## Acceptance Criteria
- [ ] Export to GPX format
- [ ] Export to KML format
- [ ] Export processed JSON
- [ ] Include all sensor data in exports
- [ ] Batch export multiple rides
- [ ] Share via standard Android share

## Success Metrics
- Export completes in < 10 seconds
- Files compatible with Strava/Google Earth

## Dependencies
- Depends on: Basic Data Processing Pipeline

## File Formats
- GPX 1.1 standard
- KML 2.2 standard
- Custom JSON schema documented" \
    "feature,ai-ready,P1-high,size-S" \
    "$MVP1_MILESTONE"

create_issue \
    "[P1] Performance Optimization" \
    "## Description
Optimize app performance for smooth operation

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P1
**Size**: M
**Category**: Core Features

## Acceptance Criteria
- [ ] Lazy load large CSV files
- [ ] Implement data pagination
- [ ] Add progress indicators
- [ ] Cache processed data
- [ ] Optimize memory usage
- [ ] Background processing for large files

## Success Metrics
- App uses < 200MB RAM
- No ANRs or crashes with large files

## Dependencies
- Depends on: Basic Data Processing Pipeline, Graph Visualization

## Performance Targets
- 60fps UI
- < 3 second operation max
- Progressive loading" \
    "feature,ai-ready,P1-high,size-M" \
    "$MVP1_MILESTONE"

# Testing Issues
echo ""
echo "📍 Creating Testing Issues..."

create_issue \
    "[P1] Unit Test Coverage" \
    "## Description
Achieve 80% unit test coverage for data processing

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P1
**Size**: M
**Category**: Testing

## Acceptance Criteria
- [ ] Test CSV parsing logic
- [ ] Test metric calculations
- [ ] Test moment detection
- [ ] Test data validators
- [ ] Test error handling
- [ ] CI/CD integration

## Success Metrics
- 80% code coverage
- All tests pass in CI

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Testing Framework
- JUnit 4
- Mockito
- Robolectric for Android" \
    "testing,ai-ready,needs-qa,P1-high,size-M" \
    "$MVP1_MILESTONE"

create_issue \
    "[P1] Integration Testing" \
    "## Description
End-to-end testing of critical user flows

## For AI Agent
**Agent**: quality-assurance-engineer
**Priority**: P1
**Size**: M
**Category**: Testing

## Acceptance Criteria
- [ ] Test recording flow
- [ ] Test data processing flow
- [ ] Test visualization flow
- [ ] Test export functionality
- [ ] Test on multiple devices
- [ ] Performance benchmarks

## Success Metrics
- All critical paths tested
- Performance within targets

## Dependencies
- Depends on: All P0 tasks

## Test Scenarios
- Record 1 hour ride
- Process and visualize
- Export to all formats" \
    "testing,ai-ready,needs-qa,P1-high,size-M" \
    "$MVP1_MILESTONE"

# P2 Medium Priority Issues
echo ""
echo "📍 Creating P2 Medium Priority Issues..."

create_issue \
    "[P2] Ride Comparison" \
    "## Description
Compare multiple rides side by side

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P2
**Size**: M
**Category**: Data Processing

## Acceptance Criteria
- [ ] Select 2-3 rides to compare
- [ ] Compare statistics table
- [ ] Overlay graphs
- [ ] Show differences/improvements
- [ ] Export comparison report

## Success Metrics
- Comparison loads in < 5 seconds
- Clear visual differentiation

## Dependencies
- Depends on: Basic Data Processing Pipeline

## UI/UX
- Split screen view
- Color coding for each ride
- Synchronized scrolling" \
    "feature,ai-ready,P2-medium,size-M" \
    "$MVP1_MILESTONE"

create_issue \
    "[P2] Basic Ride Segments" \
    "## Description
Automatically segment rides into logical sections

## For AI Agent
**Agent**: senior-feature-developer
**Priority**: P2
**Size**: S
**Category**: Data Processing

## Acceptance Criteria
- [ ] Detect stops > 1 minute
- [ ] Split ride into segments
- [ ] Show segment statistics
- [ ] Name segments (optional)
- [ ] Merge/split segments manually

## Success Metrics
- 95% accuracy in stop detection
- Segments clearly displayed

## Dependencies
- Depends on: Basic Data Processing Pipeline

## Algorithm
- GPS speed-based detection
- Configurable stop threshold" \
    "feature,ai-ready,P2-medium,size-S" \
    "$MVP1_MILESTONE"

echo ""
echo "✅ Issue creation complete!"
echo ""
echo "Next steps:"
echo "1. View issues: gh issue list"
echo "2. View by priority: gh issue list --label P0-critical"
echo "3. Start work: 'Work on issue #1'"
echo ""
echo "Happy coding! 🏍️"