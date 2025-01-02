# llama-cpp dockerized build

A docker-based setup to build [llama-cpp](todo) binaries:

* supports CUDA and CPU based builds
* cross-build for Linux x86_64 and arm64

Used as a base for [nixiesearch/nixiesearch](https://github.com/nixiesearch/nixiesearch) embedded llamacpp-server.

## Usage

Create the build container:

```shell
docker build -t llamacpp-build:latest --platform=x86_64 .
```

Build the binary without CUDA:

```shell
docker run -i -t -e "LLAMACPP_GPU=false" -v ./llama.cpp:/llama.cpp llamacpp-server-bin:latest
```

OR with CUDA:
```shell
docker run -i -t -e "LLAMACPP_GPU=true" -v ./llama.cpp:/llama.cpp llamacpp-server-bin:latest
```

## Binaries

Resulting binaries are going to be found in `llama.cpp/build/bin`

## License

Apache 2.0