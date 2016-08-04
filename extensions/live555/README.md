# ExoPlayer LIVE555 Extension #

## Description ##

The LIVE555 Extension is ...

## Build Instructions ##

* Checkout ExoPlayer along with Extensions:

```
git clone https://github.com/google/ExoPlayer.git
```

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
LIVE555_EXT_PATH="${EXOPLAYER_ROOT}/extensions/live555/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

```
NDK_PATH="<path to Android NDK>"
```

* Fetch live555:

```
cd "${LIVE555_EXT_PATH}/jni" && \
curl http://www.live555.com/liveMedia/public/live555-latest.tar.gz | tar xfz -
```

* Build the JNI native libraries from the command line:

```
cd "${LIVE555_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4
```

* In your project, you can add a dependency to the live555 Extension by using a the
  following rule:

```
// in settings.gradle
include ':..:ExoPlayer:library'
include ':..:ExoPlayer:extension-live555'

// in build.gradle
dependencies {
    compile project(':..:ExoPlayer:library')
    compile project(':..:ExoPlayer:extension-live555')
}
```

* Now, when you build your app, the live555 extension will be built and the native
  libraries will be packaged along with the APK.
