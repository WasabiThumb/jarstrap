cmake_minimum_required(VERSION 3.20)
project(jarstrap C)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fPIC")
set(CMAKE_C_STANDARD 99)

file(GLOB SRC_FILES src/*.c)
add_executable(${PROJECT_NAME} main.c ${SRC_FILES})

set( RC_DEPENDS "" )
function( add_resource input )
    string( MAKE_C_IDENTIFIER ${input} input_identifier )
    set( input "../${input}" )
    set( output  "${CMAKE_BINARY_DIR}/${input_identifier}.o" )
    target_link_libraries( ${PROJECT_NAME} ${output} )

    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    add_custom_command(
            OUTPUT ${output}
            COMMAND ${CMAKE_LINKER} --relocatable --format binary --output ${output} ${input}
            DEPENDS ${input}
    )
    elseif(CMAKE_SIZEOF_VOID_P EQUAL 4)
        add_custom_command(
                OUTPUT ${output}
                COMMAND ${CMAKE_LINKER} -melf_i386 --relocatable --format binary --output ${output} ${input}
                DEPENDS ${input}
        )
    endif()

    set( RC_DEPENDS ${RC_DEPENDS} ${output} PARENT_SCOPE )
endfunction()
add_resource( "archive/archive.jar" )
add_custom_target( rc ALL DEPENDS ${RC_DEPENDS} )

add_dependencies(${PROJECT_NAME} rc)