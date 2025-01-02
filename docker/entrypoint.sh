#!/bin/bash

set -euxo pipefail

echo "Building llama.cpp TAG=$LLAMACPP_TAG GPU=$LLAMACPP_GPU:"

git clone --depth 1 --branch $LLAMACPP_TAG https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
DEFAULT_ARGS="-DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF -DGGML_AVX512=OFF"
ARCH=`uname -p`


if [ "$LLAMACPP_GPU" == "true" ]; then
  cmake -B build $DEFAULT_ARGS -DGGML_CUDA=ON -DCMAKE_CUDA_COMPILER=/usr/local/cuda-12.4/bin/nvcc
  OUT_DIR="/out/linux/$ARCH/cu12/"
else
  cmake -B build $DEFAULT_ARGS
  OUT_DIR="/out/linux/$ARCH/cpu/"
fi
echo "Config done, running build..."
cmake --build build --config Release -j12 -t llama-server

echo "Build done, copying files to $OUT_DIR"
mkdir -p $OUT_DIR
cp -av build/bin/llama-server $OUT_DIR
find . -name "*.so" -exec cp {} $OUT_DIR \;
