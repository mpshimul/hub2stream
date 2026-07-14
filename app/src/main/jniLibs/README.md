# FFmpeg Native Libraries for Media3

## Why are these needed?

Some IPTV streams use MP2 (MPEG-1 Audio Layer II) codec which is NOT supported by most Android hardware decoders.
The Media3 FFmpeg extension (`media3-ext-ffmpeg`) provides a software decoder that can handle MP2 and other codecs.

Without these native libraries, streams with MP2 audio will play silently (no sound).

## How to build

1. Install the Android NDK (via Android Studio SDK Manager or standalone)

2. Clone the Media3 repository:
   ```
   git clone https://github.com/androidx/media.git
   cd media
   git checkout release-1.4.0
   ```

3. Set environment variables:
   ```
   export NDK_PATH=/path/to/android-ndk  # e.g., ~/Android/Sdk/ndk/26.1.10909125
   export HOST_PLATFORM=linux-x86_64      # or darwin-x86_64 for macOS
   ```

4. Build FFmpeg:
   ```
   cd libraries/decoder_ffmpeg/src/main/jni
   ./build_ffmpeg.sh --arch arm --enable-decoder=mp2,mp3,aac,ac3,eac3,flac,opus,vorbis
   ./build_ffmpeg.sh --arch arm64 --enable-decoder=mp2,mp3,aac,ac3,eac3,flac,opus,vorbis
   ```

5. Build the JNI wrapper:
   ```
   cd ../..
   # Build for each architecture
   $NDK_PATH/ndk-build \
     APP_ABI=armeabi-v7a,arm64-v8a \
     APP_PLATFORM=android-23 \
     NDK_PROJECT_PATH=. \
     NDK_APPLICATION_MK=src/main/jni/Application.mk
   ```

6. Copy the built .so files:
   ```
   cp obj/local/armeabi-v7a/libffmpeg_jni.so /path/to/app/src/main/jniLibs/armeabi-v7a/
   cp obj/local/arm64-v8a/libffmpeg_jni.so /path/to/app/src/main/jniLibs/arm64-v8a/
   ```

## Supported architectures
- `armeabi-v7a` - 32-bit ARM (older devices)
- `arm64-v8a` - 64-bit ARM (modern devices, recommended)
- `x86` - Intel x86 (emulators)
- `x86_64` - Intel x86_64 (emulators)

## Minimum build (MP2 only)
If you only need MP2 audio support, build with minimal codecs:
```
./build_ffmpeg.sh --arch arm64 --enable-decoder=mp2
```

This significantly reduces the .so file size (~1MB vs ~5MB for all codecs).

## Verification
After building and including the .so files, check Logcat for:
```
D/LivePlayerScreen: Player created with OkHttp DataSource + FFmpeg renderer + optimized buffers
D/LivePlayerScreen: [Audio Track] mime=audio/mpeg-L2, ...
W/LivePlayerScreen: ⚠️ MP2/MPEG audio detected!
```

If FFmpeg is working, you should see audio output for MP2 streams.
If not, you'll see warnings about missing native libraries.
