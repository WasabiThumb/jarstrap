#include "util.h"

char util_nibble2hex(uint8_t nibble) {
    if (nibble > 9) {
        return (char) ((nibble - 10) + 'A');
    } else {
        return (char) (nibble + '0');
    }
}

const char* util_uint2hex(uint32_t uint, char* hex) {
    uint8_t* bytes = (uint8_t*) &uint;
    uint8_t b;
    int n;
    for (int i=0; i < 4; i++) {
        b = bytes[i];
        n = i << 1;
        hex[n] = util_nibble2hex(b >> 4);
        hex[n | 1] = util_nibble2hex(b & 0xF);
    }
    hex[8] = (char) 0;
}

uint32_t util_fast_hash(const unsigned char* dat, size_t len) {
    uint32_t ret = 0x811C9DC5;
    for (int i=0; i < len; i++) {
        ret = (dat[i] ^ ret) * 0x01000193;
    }
    return ret;
}
