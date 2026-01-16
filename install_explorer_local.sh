#!/usr/bin/env bash

ms_path_explorer_jar_name="mapstruct-path-explorer.jar"
ms_path_explorer_dir="$HOME/tools/java-extensions/mapstruct"
ms_path_explorer_jar="$ms_path_explorer_dir/mapstruct-path-explorer.jar"
if [ -f "$ms_path_explorer_jar" ]; then
    echo "$ms_path_explorer_jar already exists, updating..."
fi

./mvnw clean package -U -DskipTests \
    && cd ./target \
    && mkdir -p "$ms_path_explorer_dir" \
    && mv "$ms_path_explorer_jar_name" "$ms_path_explorer_dir/"

echo "local $ms_path_explorer_jar_name successfully installed to $ms_path_explorer_dir"
