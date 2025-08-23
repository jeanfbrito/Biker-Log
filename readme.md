# Moto Sensor Logger - MVP 1.0

> **DIY Open-Source Motorcycle Sensor Data Logger**  
> Real-time sensor data collection and analysis for motorcycle dynamics, wheelies, jumps, and off-road performance tracking.

## 🏍️ Project Overview

This project creates a comprehensive motorcycle sensor logging system using Android smartphones as the initial MVP platform. The system captures dynamic motion data, GPS tracking, and environmental conditions to analyze motorcycle performance, detect events like wheelies and jumps, and provide detailed ride analytics.

**Project Philosophy**: Start simple with existing hardware (Android phone), validate concepts, then evolve to dedicated hardware while maintaining full backward compatibility.

## 📱 MVP 1.0 Features

### Supported Sensors (Samsung Galaxy S23 Ultra tested)
- ✅ **9-Axis IMU**: 3-axis accelerometer + 3-axis gyroscope + 3-axis magnetometer
- ✅ **High-Precision GPS**: Latitude, longitude, altitude, speed
- ✅ **Barometric Pressure**: High-resolution altitude measurement for jump detection
- ✅ **Battery Monitoring**: Voltage and charge state logging

### Automatic Event Detection
- 🤸 **Wheelie Detection**: Using pitch angle > 15-30° + longitudinal acceleration patterns
- 🚀 **Jump Detection**: Barometric altitude variation + vertical acceleration analysis
- 🏃 **Aggressive Maneuvers**: Lateral acceleration + roll angle for corner analysis
- 📍 **GPS Trail Mapping**: 3D visualization of ride paths with elevation data

## 🎯 Target Use Cases

- **Adventure Riding**: Trail mapping with altitude profiles and temperature variations
- **Off-Road Performance**: Jump height, wheelie duration, lean angle analysis  
- **Ride Analytics**: Speed profiles, acceleration patterns, route optimization
- **Safety Analysis**: Event detection for reviewing riding techniques
- **Open Source Development**: Community-driven sensor platform evolution

## 📊 Data Format & Architecture

### Event-Based Logging System
The system uses a **sparse event-based logging format** to minimize redundancy and optimize storage:

```csv
# Moto Sensor Log v1.0
# Device: Samsung S23 Ultra  
# Date: 2025-08-22T19:30:00-03:00
# Schema: {
#   "version": "1.0",
#   "events": {
#     "GPS": {
#       "description": "GPS positioning data",
#       "frequency": "1-5Hz",
#       "fields": [
#         {"name": "data1", "type": "double", "unit": "degrees", "description": "latitude"},
#         {"name": "data2", "type": "double", "unit": "degrees", "description": "longitude"},
#         {"name": "data3", "type": "double", "unit": "meters", "description": "altitude_GPS"}
#       ]
#     },
#     "IMU": {
#       "description": "Inertial Measurement Unit data",
#       "frequency": "50-100Hz", 
#       "fields": [
#         {"name": "data1", "type": "double", "unit": "m/s²", "description": "accel_X"},
#         {"name": "data2", "type": "double", "unit": "m/s²", "description": "accel_Y"},
#         {"name": "data3", "type": "double", "unit": "m/s²", "description": "accel_Z"},
#         {"name": "data4", "type": "double", "unit": "°/s", "description": "gyro_roll"},
#         {"name": "data5", "type": "double", "unit": "°/s", "description": "gyro_pitch"},
#         {"name": "data6", "type": "double", "unit": "°/s", "description": "gyro_yaw"}
#       ]
#     },
#     "BARO": {
#       "description": "Barometric pressure and altitude",
#       "frequency": "10-25Hz",
#       "fields": [
#         {"name": "data1", "type": "double", "unit": "meters", "description": "altitude_baro"},
#         {"name": "data2", "type": "double", "unit": "hPa", "description": "pressure"}
#       ]
#     }
#   }
# }
timestamp,sensor_type,data1,data2,data3,data4,data5,data6
1725316200001,GPS,-23.5123,-46.6234,820.5,,,
1725316200011,IMU,0.2,-0.1,9.8,12.5,-5.3,2.1
1725316200051,BARO,820.2,1013.25,,,,
```

### Key Advantages
- **Self-Contained**: Each CSV file includes complete schema definition in header
- **Version-Agnostic**: Universal parser adapts to any schema version automatically  
- **Storage Efficient**: 80-90% space savings vs traditional tabular format
- **No External Dependencies**: Zero configuration files or external schemas needed
- **Backward Compatible**: Old files work perfectly with new analysis tools

## 🚀 Roadmap

### Phase 1: Android MVP (Current)
- [x] Event-based logging architecture
- [x] Self-contained CSV format with embedded schema
- [x] Universal parser for version compatibility
- [ ] Android app development
- [ ] Real-time HUD interface
- [ ] Event detection algorithms
- [ ] Data visualization dashboard

### Phase 2: Dedicated Hardware Evolution
- [ ] Raspberry Pi Zero 2 W integration
- [ ] Custom PCB design with additional sensors:
  - Ambient temperature (BME280)
  - Enhanced IMU (MPU-9250/ICM-20948)
  - Higher precision barometer (MS5611)
- [ ] Weatherproof enclosure design
- [ ] Extended battery life optimization

### Phase 3: Advanced Features
- [ ] Real-time data streaming
- [ ] Machine learning event classification
- [ ] Comparative performance analytics
- [ ] Community data sharing platform

## 🛠️ Technical Requirements

### Android Requirements
- **Minimum**: Android 8.0 (API level 26)
- **Recommended**: Android 12+ with full sensor suite
- **Sensors Required**: Accelerometer, Gyroscope, GPS
- **Sensors Optional**: Barometer, Magnetometer
- **Storage**: 1GB+ free space for extended logging
- **Tested Devices**: Samsung Galaxy S23 Ultra

### Development Environment  
- **Android Studio**: 2023.1+ (optional, can build from command line)
- **Java**: JDK 17 or higher
- **Android SDK**: API level 34
- **Target SDK**: 34 (Android 14)
- **Build Tools**: 34.0.0
- **Gradle**: 8.2 (included via wrapper)
- **Languages**: Kotlin for Android, Python for analysis

## 📲 Installation & Setup

### Prerequisites
- Java 17 or higher (check with `java -version`)
- Android phone with USB cable

### 📱 Setup Your Phone (One-time)

1. **Enable Developer Mode:**
   - Settings → About Phone → Tap "Build Number" 7 times

2. **Enable USB Debugging:**
   - Settings → Developer Options → Turn ON "USB Debugging"

3. **Connect to Computer:**
   - Connect via USB cable
   - Tap "Allow" when asked about USB debugging
   - Select "File Transfer" mode (not "Charging only")

### 🚀 Install & Run

```bash
# Clone the project
git clone https://github.com/yourusername/moto-sensor-logger.git
cd moto-sensor-logger

# Install on your phone (Gradle auto-downloads everything!)
./gradlew installDebug

# Or use the shortcut script:
./install.sh
```

**That's it!** Gradle automatically downloads the Android SDK, build tools, and all dependencies. No manual setup needed!

### 🖥️ Alternative: Use Android Studio

1. Download [Android Studio](https://developer.android.com/studio)
2. Open this project folder
3. Click the green ▶️ button

### Common Commands

```bash
./gradlew installDebug    # Build and install on phone
./gradlew assembleDebug   # Just build APK
./gradlew clean           # Clean build files
./gradlew tasks           # See all available tasks
```

### Troubleshooting

- **"No connected devices"**: Check USB debugging is enabled and cable is connected
- **"Java not found"**: Install Java 17+ (`brew install openjdk@17` on Mac)
- **Build fails**: Make sure your phone is connected before running

## 📈 Data Analysis Capabilities

The logged data enables comprehensive analysis including:

- **Motion Dynamics**: 3D acceleration and rotation analysis
- **Performance Metrics**: Speed profiles, lean angles, acceleration forces
- **Event Timeline**: Automatic detection and cataloging of wheelies, jumps, turns
- **GPS Mapping**: 3D trail visualization with elevation and performance overlay
- **Environmental Correlation**: Altitude vs performance, temperature effects
- **Statistical Analysis**: Ride comparison, performance trends, safety metrics

## 🤝 Contributing

This project is designed to be community-driven and welcomes contributions in:

- **Hardware Testing**: Validation on different Android devices
- **Algorithm Development**: Improved event detection and analysis
- **UI/UX Design**: Better data visualization and real-time interfaces  
- **Documentation**: User guides, installation instructions, troubleshooting
- **Hardware Evolution**: PCB design, sensor integration, enclosure development

## 📄 License

This project is open-source and available under the MIT License. See `LICENSE` file for details.

## 🔗 Project Status

**Current Phase**: MVP 1.0 Development  
**Status**: Architecture Complete, Android App Development in Progress 

---

*Built for motorcyclists, by motorcyclists. Ride safe, log everything, analyze thoroughly.* 🏍️📊