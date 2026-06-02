# Hub2Stream 📺

<div align="center">

![Hub2Stream Logo](https://img.shields.io/badge/Hub2Stream-v1.0.2-brightgreen)
![Android](https://img.shields.io/badge/Android-5.0%2B-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-ff69b4)
![License](https://img.shields.io/badge/License-MIT-green)

**All-in-One Streaming App for Movies, TV Series, Live TV & Sports**

[📥 Download APK](https://github.com/mpshimul/hub2stream/releases/latest/download/app-release.apk) • [⭐ Star](#) • [🐛 Report Issue](https://github.com/mpshimul/hub2stream/issues)

</div>

---

## 📱 About Hub2Stream

Hub2Stream is a modern, feature-rich Android streaming application built with Jetpack Compose. It offers a seamless viewing experience for movies, TV series, live TV channels, and sports events - all in one app. With support for TV remotes, Picture-in-Picture mode, and a beautiful user interface, Hub2Stream delivers an exceptional streaming experience on both mobile devices and Android TV.

### 🌟 Key Features

#### 🎬 **Content Library**
- **Movies & TV Series** - Extensive library of on-demand content
- **Live TV Channels** - Watch live television streams
- **Sports Coverage** - Live sports events and matches
- **FIFA World Cup** - Dedicated section for World Cup matches with live updates

#### 🎮 **TV Remote Support**
- Full D-Pad navigation support
- Optimized for Android TV and Fire TV
- Voice search compatible
- TV-friendly UI with focus indicators

#### 🖼️ **Picture-in-Picture Mode**
- Continue watching while browsing other apps
- Auto-enters PIP when pressing Home
- Supports both portrait and landscape orientations
- Available on Android 8.0+ devices

#### ⚙️ **Playback Features**
- **Multiple Quality Options** - Choose your preferred streaming quality
- **Subtitle Support** - Multi-language subtitles
- **Audio Track Selection** - Switch between audio tracks and dubs
- **Playback Speed Control** - 0.5x to 2.0x speed
- **Aspect Ratio Modes** - Fit, Zoom, Fill, Fixed Width/Height
- **Auto-Resume** - Continue watching from where you left off

#### 📊 **User Experience**
- **Continue Watching** - Quick access to recently watched content
- **Favorites** - Save your favorite movies and shows
- **Smart Search** - Find content quickly
- **Smooth Animations** - Modern, fluid UI transitions
- **Dark Theme** - Comfortable viewing experience

#### 🎯 **Additional Features**
- **Episode Navigation** - Easy series episode switching
- **Channel Switching** - Quick live TV channel change
- **Error Recovery** - Automatic stream retry mechanism
- **Video Downloads** - Download content for offline viewing
- **Auto-Update** - Built-in update checker

---

## 📸 Screenshots

| Home Screen | Live TV | Player |
|-------------|---------|--------|
| ![Home](https://via.placeholder.com/300x500/1a1a2e/fff?text=Home+Screen) | ![Live TV](https://via.placeholder.com/300x500/1a1a2e/fff?text=Live+TV) | ![Player](https://via.placeholder.com/300x500/1a1a2e/fff?text=Video+Player) |

| Settings | Picture-in-Picture | Search |
|----------|-------------------|--------|
| ![Settings](https://via.placeholder.com/300x500/1a1a2e/fff?text=Settings) | ![PIP](https://via.placeholder.com/300x500/1a1a2e/fff?text=PiP+Mode) | ![Search](https://via.placeholder.com/300x500/1a1a2e/fff?text=Search) |

---

## 📥 Download

### Latest Version: 1.0.0

| File | Size | Downloads | Updated |
|------|------|-----------|---------|
| [Hub2Stream-v1.0.0.apk](releases/latest/download/app-release.apk) | ~15 MB | ![Downloads](https://img.shields.io/github/downloads/mpshimul/Hub2Stream/latest/total) | 2026 |

### Installation

1. Download the APK from the link above
2. Enable "Unknown Sources" in your device settings:
   - Go to **Settings > Security > Unknown Sources** (Android 7 and below)
   - Or allow your browser to install apps (Android 8+)
3. Tap the downloaded APK file to install
4. Enjoy streaming!

**For Android TV / Fire TV:**
- Download the APK to a USB drive
- Plug the USB drive into your TV
- Use a file manager app to locate and install the APK
- Or use ADB: `adb install Hub2Stream-v1.0.0.apk`

---

## 📋 Requirements

- **Android Version**: 5.0 (Lollipop) or higher
- **Device Type**: Android phones, tablets, Android TV, Fire TV Stick
- **Storage**: 50 MB free space + space for downloads
- **Internet**: Stable internet connection (Wi-Fi recommended for HD streaming)
- **RAM**: 2 GB or more recommended for smooth playback
- **PiP Support**: Android 8.0 (Oreo) or higher for Picture-in-Picture mode

---

## 🚀 Features in Detail

### 🎬 Video Player
- **ExoPlayer Integration** - Advanced media player with HLS support
- **Gesture Controls** - Swipe to seek, pinch to zoom
- **Hardware Acceleration** - Smooth playback on most devices
- **Background Playback** - Audio-only playback option
- **Adaptive Bitrate** - Automatically adjusts quality based on connection

### 📺 Live TV
- **Channel Categories** - Organized by genre and region
- **EPG Support** - Electronic Program Guide where available
- **Quick Channel Switch** - Previous/Next channel buttons
- **Channel Favorites** - Save preferred channels

### ⚽ FIFA World Cup
- **Live Match Coverage** - Real-time match streaming
- **Match Schedule** - Upcoming matches with countdown timer
- **Team Information** - Match details and teams
- **Multiple Streams** - Multiple stream sources for reliability

### 🎨 User Interface
- **Modern Design** - Built with Jetpack Compose
- **Focus Accent** - Gold-themed focus indicators
- **Responsive Layout** - Adapts to different screen sizes
- **Accessibility** - Screen reader support, high contrast mode

---

## 🔧 Technical Details

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with ViewModel
- **Navigation**: Jetpack Navigation Compose
- **Dependency Injection**: Manual DI
- **Networking**: Kotlin Coroutines + HTTP Client
- **Image Loading**: Coil
- **Video Player**: ExoPlayer (Media3)
- **Data Persistence**: DataStore (Preferences)
- **Serialization**: Kotlin Serialization + Jackson

### Libraries Used
```kotlin
// Core
androidx.core:core-ktx:1.12.0
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0
androidx.activity:activity-compose:1.8.2

// Jetpack Compose
androidx.compose:compose-bom:2024.02.00
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended

// Navigation
androidx.navigation:navigation-compose:2.7.6

// Media
androidx.media3:media3-exoplayer:1.4.0
androidx.media3:media3-ui:1.4.0
androidx.media3:media3-exoplayer-hls:1.4.0

// Image Loading
io.coil-kt:coil-compose:2.5.0

// Data
androidx.datastore:datastore-preferences:1.0.0
org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0
com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
```

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Koala or later
- JDK 17
- Android SDK 34

### Steps

1. Clone the repository:
```bash
git clone https://github.com/shimulfp/Hub2Stream.git
cd Hub2Stream
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build the project:
```bash
./gradlew assembleDebug
```

5. The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Generate Release APK
```bash
./gradlew assembleRelease
```

The release APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 📱 Supported Devices

### Tested On
- ✅ Android TV
- ✅ Fire TV Stick 4K
- ✅ Fire TV Stick Lite
- ✅ Xiaomi Mi Box
- ✅ Nvidia Shield TV
- ✅ Chromecast with Google TV
- ✅ Android Smart TVs (Sony, TCL, Samsung, etc.)
- ✅ Android Phones & Tablets

### Minimum Requirements
- Android 5.0 (API 23)
- 1GB RAM
- 50MB free storage

### Recommended Requirements
- Android 9.0 (API 28) or higher
- 2GB+ RAM
- 100MB+ free storage
- Stable internet connection

---

## 🗺️ Roadmap

### Upcoming Features
- [ ] Chromecast support
- [ ] Parental controls
- [ ] Watch party feature
- [ ] More subtitle formats (VTT, ASS, SAA)
- [ ] Multiple user profiles
- [ ] Advanced search filters
- [ ] Playlist creation
- [ ] Video casting to other devices
- [ ] Dark/Light theme toggle
- [ ] Custom video filters

### Improvements
- [ ] Better error handling
- [ ] Faster loading times
- [ ] Reduced app size
- [ ] Enhanced TV UI
- [ ] Voice command support

---

## 🤝 Contributing

Contributions are welcome! If you'd like to contribute to Hub2Stream:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Guidelines
- Follow Kotlin coding conventions
- Write clean, documented code
- Test on multiple devices if possible
- Ensure TV compatibility for UI changes

---

## 📄 License

```
MIT License

Copyright (c) 2024 Shimul FP

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## ⚠️ Disclaimer

This application is for educational and personal use only. The developers do not host, upload, or distribute any copyrighted content. All content is streamed from third-party sources. Users are responsible for ensuring they comply with their local copyright laws.

---

## 📞 Support

### Need Help?
- 📧 Email: support@
- 💬 Telegram: [Hub2Stream Community](https://t.me/)
- 🐛 [Report a Bug](https://github.com/mpshimul/Hub2Stream/issues)
- 💡 [Feature Request](https://github.com/mpshimul/Hub2Stream/issues)

### Social Media
- [Twitter/X](https://twitter.com/)
- [Facebook](https://facebook.com/)
- [Instagram](https://instagram.com/)

---

## 🙏 Acknowledgments

- **ExoPlayer** by Google for the powerful media player
- **Jetpack Compose** team for the modern UI toolkit
- **Coil** for efficient image loading
- The open-source community for various libraries

---

## 📊 Stats

![GitHub stars](https://img.shields.io/github/stars/shimulfp/Hub2Stream?style=social)
![GitHub forks](https://img.shields.io/github/forks/shimulfp/Hub2Stream?style=social)
![GitHub watchers](https://img.shields.io/github/watchers/shimulfp/Hub2Stream?style=social)
![GitHub issues](https://img.shields.io/github/issues/shimulfp/Hub2Stream)
![GitHub license](https://img.shields.io/github/license/shimulfp/Hub2Stream)

---

<div align="center">

**Made with ❤️ by MPSHIMUL**

[⬆ Back to Top](#hub2stream-)

</div>
