cmake_minimum_required(VERSION 3.22.1)

# AGP passes CMAKE_BUILD_TYPE=Debug for the app's debug build variant, which otherwise
# leaves whisper.cpp/ggml's compute-heavy kernels compiled at -O0 (confirmed via
# compile_commands.json — no -O flag at all). The debug/release split only matters for
# our own thin JNI glue below, never for whisper.cpp's internals, so force Release here
# regardless of which Gradle variant is building.
set(CMAKE_BUILD_TYPE "Release" CACHE STRING "" FORCE)

project(diary_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_USE_CPU ON CACHE BOOL "" FORCE)

# Enables ARMv8.2 dot-product and fp16-vector instructions in ggml's CPU backend —
# matches whisper.cpp's own official Android sample (examples/whisper.android). Real
# speedup for quantized-model matmuls on modern phone SoCs (confirmed present on the
# Exynos 1380 in the A54: Cortex-A78/A55, both ARMv8.2 with DOTPROD+FP16). Only read for
# GGML_SYSTEM_ARCH=ARM builds, so this is a no-op for the x86_64 emulator target.
# NOTE: not all arm64-v8a devices support these extensions (older/budget ARMv8.0 chips
# don't) — fine for dev-testing now, but revisit before shipping to a broad device
# fleet (either drop back to baseline armv8-a, or use ggml's GGML_CPU_ALL_VARIANTS +
# GGML_BACKEND_DL runtime-dispatch build for real portability).
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(whisper.cpp)

add_library(diary_whisper SHARED
    whisper_jni.cpp
)

target_include_directories(diary_whisper PRIVATE
    whisper.cpp/include
)

target_link_libraries(diary_whisper
    whisper
    android
    log
)
