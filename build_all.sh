#!/bin/sh

set -euxo pipefail

LLAMACPP_TAG=$1

./build_cpu.sh $LLAMACPP_TAG x86_64
./build_cpu.sh $LLAMACPP_TAG arm64
./build_cuda.sh $LLAMACPP_TAG