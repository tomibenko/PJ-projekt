# Official android sdk image
FROM openjdk:11

# Install Android SDK tools
RUN apt-get update && apt-get install -y wget unzip
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-7302050_latest.zip -O /sdk.zip
RUN mkdir /sdk && unzip /sdk.zip -d /sdk
RUN yes | /sdk/cmdline-tools/bin/sdkmanager --sdk_root=/sdk --licenses
RUN /sdk/cmdline-tools/bin/sdkmanager --sdk_root=/sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Set environment variables
ENV ANDROID_HOME /sdk
ENV PATH $ANDROID_HOME/platform-tools:$PATH
ENV PATH $ANDROID_HOME/cmdline-tools/tools/bin:$PATH
ENV PATH $ANDROID_HOME/build-tools/34.0.0:$PATH

# Copy project files
WORKDIR /app
COPY . /app

# Run gradle build
RUN ./gradlew build

# Expose port
EXPOSE 8080

# Default command
CMD ["./gradlew", "assembleRelease"]