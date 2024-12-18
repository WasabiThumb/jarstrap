cmake_minimum_required(VERSION 3.17)
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

    # objcopy -I binary -O elf64-x86-64 archive.jar archive.o
    if(WIN32)
        get_filename_component(toolDir "${CMAKE_LINKER}" DIRECTORY)
        find_file(tool "objcopy.exe" PATHS "${toolDir}")

        if(CMAKE_SIZEOF_VOID_P EQUAL 8)
            add_custom_command(
                    OUTPUT ${output}
                    COMMAND "${tool}" -I binary -O elf64-x86-64 ${input} ${output}
                    DEPENDS ${input}
            )
        elseif(CMAKE_SIZEOF_VOID_P EQUAL 4)
            add_custom_command(
                    OUTPUT ${output}
                    COMMAND "${tool}" -I binary -O elf32-i386 ${input} ${output}
                    DEPENDS ${input}
            )
        endif()
    else()
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
    endif()

    set( RC_DEPENDS ${RC_DEPENDS} ${output} PARENT_SCOPE )
endfunction()
add_resource( "archive/archive.jar" )
add_custom_target( rc ALL DEPENDS ${RC_DEPENDS} )

add_dependencies(${PROJECT_NAME} rc)
if(WIN32)
    target_link_libraries(${PROJECT_NAME} winhttp)
endif()