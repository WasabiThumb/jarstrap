#include "version.h"

// VERSION START
static const char JARSTRAP_VERSION[] = "0.0.1";
static const char JARSTRAP_COPYRIGHT_YEAR[] = "2024";
// VERSION END

const char* version_get() {
    return JARSTRAP_VERSION;
}

const char* version_get_copyright_year() {
    return JARSTRAP_COPYRIGHT_YEAR;
}
