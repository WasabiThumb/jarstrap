#include "archive.h"

extern char binary_start[] asm("_binary____archive_archive_jar_start");
extern char binary_end[] asm("_binary____archive_archive_jar_end");

void archive_get(unsigned char** buf, size_t* size) {
    *size = binary_end - binary_start;
    *buf = (unsigned char*) binary_start;
}
