FROM eclipse-temurin:17-jdk

# Update and install required tools
RUN apt-get update && apt-get install -y unzip git curl \
   && rm -rf /var/lib/apt/lists/*

# Need a real user for build, Android SDK and Gradle
ARG UNAME=build
ARG UID=503
ARG GID=20
RUN groupadd -g $GID -o $UNAME && \
    useradd -m -u $UID -g $GID -o -s /bin/bash $UNAME
USER $UNAME
ENV HOME=/home/$UNAME
ENV ANDROID_SDK_ROOT="/home/$UNAME/Android/sdk"
ENV PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"

# Bootstrap commandlinetools to get sdkmanager
ENV CMDLINE_TOOLS="commandlinetools-linux-8512546_latest.zip"
RUN curl -s https://dl.google.com/android/repository/$CMDLINE_TOOLS > /tmp/$CMDLINE_TOOLS \
   && unzip -d /tmp /tmp/$CMDLINE_TOOLS \
   && yes | /tmp/cmdline-tools/bin/sdkmanager --sdk_root=$ANDROID_SDK_ROOT "cmdline-tools;7.0" \
   && ln -s $ANDROID_SDK_ROOT/cmdline-tools/7.0 $ANDROID_SDK_ROOT/cmdline-tools/latest

# Install Android SDK platforms and build tools for AndroidX Media3
RUN yes | sdkmanager --licenses \
    && yes | sdkmanager "platform-tools" "cmdline-tools;7.0" "build-tools;34.0.0" \
        "platforms;android-21" "platforms;android-29" "platforms;android-30" "platforms;android-34" \
    && sdkmanager --list
