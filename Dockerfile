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

# Bootstrap a version (7.0, https://developer.android.com/studio/releases/cmdline-tools) of commandlinetools
# to get a workable sdkmanager.  Then use that sdkmanager to install latest
#
ENV CMDLINE_TOOLS="commandlinetools-linux-8512546_latest.zip"
RUN if which sdkmanager; then echo "using sdkmanager: $(which sdkmanager)"; else curl -s https://dl.google.com/android/repository/$CMDLINE_TOOLS > /tmp/$CMDLINE_TOOLS \
   && unzip -d /tmp /tmp/$CMDLINE_TOOLS \
   && yes | /tmp/cmdline-tools/bin/sdkmanager --sdk_root=$ANDROID_SDK_ROOT "cmdline-tools;7.0" \
   && ln -s $ANDROID_SDK_ROOT/cmdline-tools/7.0 $ANDROID_SDK_ROOT/cmdline-tools/latest; fi

#
# Use Android's SDK manager to install the needed platforms and tools, this is in it's own layer to allow
# containers with alternate platforms to reuse the base SDK tools.  It is also possible to install
# or patch the SDK's in gradle within the running container as this is installed in the writeable userhome
#
RUN yes | sdkmanager  --licenses \
    && yes | sdkmanager "platform-tools" "cmdline-tools;7.0" "build-tools;31.0.0" "platforms;android-29" "platforms;android-30" \
    && sdkmanager --list


