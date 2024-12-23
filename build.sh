#!/bin/bash

set -euxo pipefail

echo "Building llama.cpp TAG=$LLAMACPP_TAG GPU=$LLAMACPP_GPU"
ls -l
git clone --depth 1 --branch $LLAMACPP_TAG https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
DEFAULT_ARGS="-DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF -DLLAMA_BUILD_SERVER=ON -DGGML_AVX512=OFF"
if [ "$LLAMACPP_GPU" == "true" ]; then
  cmake -B build $DEFAULT_ARGS -DGGML_CUDA=ON -DCMAKE_CUDA_COMPILER=/usr/local/cuda-12.4/bin/nvcc
else
  cmake -B build $DEFAULT_ARGS
fi

cmake --build build --config Release -j8