cmake_minimum_required(VERSION 3.10)
project(pvz_ai CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(SDL2 REQUIRED sdl2)
pkg_check_modules(SDL2_TTF REQUIRED SDL2_ttf)

add_executable(pvz_ai src/main.cpp)

target_include_directories(pvz_ai PRIVATE ${SDL2_INCLUDE_DIRS} ${SDL2_TTF_INCLUDE_DIRS})
target_link_directories(pvz_ai PRIVATE ${SDL2_LIBRARY_DIRS} ${SDL2_TTF_LIBRARY_DIRS})
target_link_libraries(pvz_ai PRIVATE ${SDL2_LIBRARIES} ${SDL2_TTF_LIBRARIES})
target_compile_options(pvz_ai PRIVATE ${SDL2_CFLAGS_OTHER} ${SDL2_TTF_CFLAGS_OTHER})
