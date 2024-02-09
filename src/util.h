#include <stdlib.h>
#include <stdint.h>

#ifndef JARSTRAP_UTIL_H
#define JARSTRAP_UTIL_H

const char* util_uint2hex(uint32_t uint, char* hex);

uint32_t util_fast_hash(const unsigned char* dat, size_t len);

#endif
