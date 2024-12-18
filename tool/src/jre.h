#include <stdbool.h>

#ifndef JARSTRAP_JRE_H
#define JARSTRAP_JRE_H

const char* jre_version_get(const char* binary);

bool jre_version_at_least(const char* version, unsigned int min);

const char* jre_locate_path();

const char* jre_locate_at_least(unsigned int min);

void jre_open_download_page(unsigned int version);

bool jre_attempt_automated_install(unsigned int min);

#endif
