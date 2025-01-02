#!/bin/bash

set -euxo pipefail

LLAMACPP_TAG=$1

echo "Starting CUDA build: TAG=$LLAMACPP_TAG"

LLAMACPP_GPU=true LLAMACPP_TAG=$LLAMACPP_TAG docker build -t llamacpp-server-build:gpu -f docker/Dockerfile.x86_64_cu12 docker

echo "Build container ready, now running the build"

docker run --ulimit core=1000000000 -e "LLAMACPP_GPU=true" -e "LLAMACPP_TAG=$LLAMACPP_TAG" -v ./src/main/resources/native:/out llamacpp-server-build:gpu


