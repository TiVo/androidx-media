FROM docker.tivo.com/openjdk-docker:11
#
# Update and install zip
RUN apt-get update && apt-get install -y unzip git \
   && rm -rf /var/lib/apt/lists/*

#
# Need a real user for build, Android SDK and Gradle, so much of Android build
# expects Java system properties (user.home, user.name) to be a real writeable file
# system location
#
ARG UNAME=build
ARG UID=503
ARG GID=20
RUN groupadd -g $GID -o $UNAME && \
    useradd -m -u $UID -g $GID -o -s /bin/bash $UNAME
USER $UNAME
ENV HOME=/home/$UNAME
ENV ANDROID_SDK_ROOT="/home/$UNAME/Android/sdk"
ENV PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"

#
# This madness shuffle is how to install 4.0 commandline tools like Android Studio does
# without installing all of Android studio
# see: https://stackoverflow.com/questions/60440509/android-command-line-tools-sdkmanager-always-shows-warning-could-not-create-se
#
#
# Basically the commandlinetools-linux-7302050_latest.zip includes one directory too high, so the monkey business here
# does the equiv of tar --strip-components=1
#
# This first RUN creates a layer with just the Android SDK tools, next specific SDKs are installed
#
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools/latest \
   && curl -s https://dl.google.com/android/repository/commandlinetools-linux-7302050_latest.zip > /tmp/cmdline-tools.zip \
   && unzip -d $ANDROID_SDK_ROOT/cmdline-tools/latest /tmp/cmdline-tools.zip \
   && mv $ANDROID_SDK_ROOT/cmdline-tools/latest/cmdline-tools/* $ANDROID_SDK_ROOT/cmdline-tools/latest/
#
# Use Android's SDK manager to install the needed platforms and tools, this is in it's own layer to allow
# containers with alternate platforms to reuse the base SDK tools.  It is also possible to install
# or patch the SDK's in gradle within the running container as this is installed in the writeable userhome
#
RUN yes | sdkmanager  --licenses \
    && yes | sdkmanager   "platform-tools" "platforms;android-29" \
    && sdkmanager --list_installed


