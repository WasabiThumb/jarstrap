#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#ifdef WIN32
#include <windows.h>
#endif
#ifdef __linux
#include <errno.h>
#endif

#ifndef JARSTRAP_DEBUG_H
#define JARSTRAP_DEBUG_H

// TYPES

typedef enum debug_err_code {
    // Unknown error
    ERR_UNKNOWN,
    // Out of memory
    ERR_NOMEM,
    // Illegal state
    ERR_ILLEGAL,
    // Buffer overflow
    ERR_OVERFLOW,
    // I/O error
    ERR_IO,
    // Network error; only used on Windows
    ERR_WWW
} debug_err_code;

// IMPL

const char* debug_err_text(debug_err_code code);

void debug_err_print(debug_err_code code, const char* file, uint32_t line);

// HELPERS

inline void* debug_ptr_check(void* ptr, const char* file, uint32_t line) {
    if (ptr == NULL) {
        debug_err_print(ERR_NOMEM, file, line);
        exit(1);
    }
    return ptr;
}
#ifdef WIN32
#define FILE_SIMPLE (strrchr(__FILE__, '\\') ? strrchr(__FILE__, '\\') + 1 : __FILE__)
#else
#define FILE_SIMPLE (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
#endif
#define PTR_CHECK(ptr) debug_ptr_check(ptr, FILE_SIMPLE, __LINE__)
#define ERR_PRINT(code) debug_err_print(code, FILE_SIMPLE, __LINE__)
#define ERR_FATAL(code) ERR_PRINT(code); exit(1)

#endif
