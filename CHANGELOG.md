# Changelog

All notable changes to Moto Sensor Logger will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions CI/CD pipeline for automated testing and builds
- Comprehensive test suite for CSV logging integrity
- Automated release workflow with APK generation
- Security scanning with Trivy and CodeQL
- Dependabot configuration for automated dependency updates
- Code quality workflows with ktlint and detekt support
- Pull request and issue templates
- Contributing guidelines (CONTRIBUTING.md)
- Project improvement roadmap (IMPROVEMENTS.md)
- PR labeler for automatic categorization
- Basic IMU noise filtering with moving average and outlier detection (#27)

### Changed
- Improved test coverage for calibration service
- Enhanced documentation structure
- Updated minimum SDK to API 30 (Android 11) for improved GNSS support

### Fixed
- Fixed UI bug where calibration elements stayed visible after recording starts
- Fixed CSV header corruption when mixing calibration states
- Resolved test compilation issues with coroutines

## [1.1.0] - 2024-11-01

### Added
- Automatic sensor calibration system
- Real-time telemetry display
- Calibration quality indicators
- Vibration baseline detection
- Extended calibration for noisy environments
- CSV format v1.1 with embedded calibration metadata

### Changed
- Improved CSV header format with JSON-like schema
- Enhanced sensor data accuracy with calibration transforms
- Updated minimum SDK to API 26 (Android 8.0)

### Fixed
- Battery drain issues during background recording
- Memory leaks in sensor service
- File permission issues on Android 13+

## [1.0.0] - 2024-10-01

### Added
- Initial release
- High-frequency sensor data recording (50-100Hz)
- GPS tracking with speed and altitude
- IMU data collection (accelerometer, gyroscope)
- Barometric pressure sensing
- Magnetometer data logging
- CSV export with metadata headers
- Background service for continuous recording
- Custom visualization views (inclinometer, G-force meter)
- Log file viewer with sharing capabilities
- Settings for sensor configuration

### Known Issues
- Calibration required for accurate lean angle measurement
- Battery optimization may interrupt long recordings
- Large CSV files may take time to load in viewer

## Pre-release Development

### [0.9.0-beta] - 2024-09-01
- Beta testing phase
- Core functionality implementation
- Initial UI design

### [0.5.0-alpha] - 2024-08-01
- Alpha release for internal testing
- Basic sensor recording functionality
- Proof of concept for data collection

---

## Version Naming Convention

- **Major version (X.0.0)**: Breaking changes or significant feature additions
- **Minor version (0.X.0)**: New features, non-breaking changes
- **Patch version (0.0.X)**: Bug fixes, minor improvements

## Release Schedule

- **Production releases**: Monthly or as needed for critical fixes
- **Beta releases**: Bi-weekly during active development
- **Alpha releases**: Continuous for testing new features

For upcoming features and roadmap, see [TASKS.md](TASKS.md).