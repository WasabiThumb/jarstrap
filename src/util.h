#include <stdlib.h>
#include <stdint.h>
#include <malloc.h>

#ifndef JARSTRAP_UTIL_H
#define JARSTRAP_UTIL_H

#ifdef __linux
#define allocarray calloc
#endif
#ifdef WIN32
#define allocarray(size, len) malloc(size * len)
#define reallocarray(ptr, size, len) realloc(ptr, size * len)
#endif

const char* util_uint2hex(uint32_t uint, char* hex);

uint32_t util_fast_hash(const unsigned char* dat, size_t len);

#endif
