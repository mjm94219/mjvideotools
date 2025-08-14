#!/bin/bash

# Get the directory where the script is located
# This ensures that we can run the script from anywhere
BASE_DIR=$(dirname "$(readlink -f "$0" || echo "$0")")

# Set the path to our bundled JRE
JAVA_CMD="$BASE_DIR/jre/bin/java"

# Set the path to our application's fat JAR
JAR_PATH="$BASE_DIR/mjvideotools-1.0-SNAPSHOT-jar-with-dependencies.jar"

# Run the application
"$JAVA_CMD" -jar "$JAR_PATH" "$@"
