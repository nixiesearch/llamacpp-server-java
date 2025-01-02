#!/bin/sh

set -euxo pipefail

LLAMACPP_TAG=$1
LLAMACPP_PLATFORM=$2

echo "Starting CPU build: TAG=$LLAMACPP_TAG LLAMACPP_PLATFORM=$LLAMACPP_PLATFORM"

LLAMACPP_GPU=false LLAMACPP_TAG=$LLAMACPP_TAG docker build -t llamacpp-server-build:cpu --platform $LLAMACPP_PLATFORM docker

echo "Build container ready, now running the build"

docker run --ulimit core=1000000000 -e "LLAMACPP_GPU=false" -e "LLAMACPP_TAG=$LLAMACPP_TAG" -v ./src/main/resources/native:/out --platform $LLAMACPP_PLATFORM llamacpp-server-build:cpu


