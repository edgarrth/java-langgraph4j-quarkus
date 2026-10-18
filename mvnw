#!/usr/bin/env bash
set -euo pipefail
maven_version=3.9.16
cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/axiz/maven"
maven_dir="$cache_dir/apache-maven-$maven_version"
if [[ ! -x "$maven_dir/bin/mvn" ]]; then
  mkdir -p "$cache_dir"
  archive="apache-maven-$maven_version-bin.tar.gz"
  url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$maven_version/$archive"
  curl --fail --location --silent --show-error "$url" -o "$cache_dir/$archive"
  curl --fail --location --silent --show-error "$url.sha512" -o "$cache_dir/$archive.sha512"
  printf '%s  %s\n' "$(cat "$cache_dir/$archive.sha512")" "$cache_dir/$archive" | sha512sum -c -
  tar -xzf "$cache_dir/$archive" -C "$cache_dir"
fi
exec "$maven_dir/bin/mvn" "$@"
