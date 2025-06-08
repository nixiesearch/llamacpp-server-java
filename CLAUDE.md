# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java wrapper library for llama.cpp server binaries that provides Docker-based cross-compilation for multiple architectures and GPU backends. The project packages pre-compiled llama.cpp binaries into a Java JAR and provides a simple API to start/stop the server process.

## Architecture

- **Core Class**: `LlamacppServer.java` - Main singleton class that manages the llama.cpp server process lifecycle
- **Binary Management**: Unpacks platform-specific binaries from JAR resources to temp directories
- **Process Management**: Handles server startup, logging, and graceful shutdown with resource cleanup
- **Backend Support**: CPU (x86_64, arm64) and CUDA12 (x86_64 only) variants

The server follows a singleton pattern where only one instance can run at a time, with automatic resource cleanup on close.

## Build Commands

**Standard Maven build:**
```bash
mvn clean package
```

**Cross-platform binary builds:**
```bash
# Build all architectures (requires Docker with binfmt)
./build_all.sh <llamacpp-tag>

# CPU-only builds
./build_cpu.sh <llamacpp-tag> x86_64
./build_cpu.sh <llamacpp-tag> arm64

# CUDA build
./build_cuda.sh <llamacpp-tag>
```

**Run tests:**
```bash
mvn test
```

**Run single test:**
```bash
mvn test -Dtest=LlamacppServerTest#testUnpack
```

## Development Notes

- Uses Maven exec plugin to trigger Docker builds during `generate-sources` phase
- Produces two JAR variants: default (CPU-only) and `cuda12-linux-x86-64` classifier
- Test downloads a small Qwen model from HuggingFace to verify server functionality
- All native binaries are extracted to `java.io.tmpdir/llamacpp` and cleaned up on close
- Server logs are forwarded to SLF4J logger at INFO level