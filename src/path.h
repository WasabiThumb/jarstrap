
#ifndef JARSTRAP_PATH_H
#define JARSTRAP_PATH_H

#ifdef WIN32
#define PATH_SEPARATOR (char) '\\'
#endif
#ifdef __linux
#define PATH_SEPARATOR (char) '/'
#endif
#define PATH_NULL (char) 0
#define PATH_DOT (char) '.'

const char* path_join(const char* restrict a, const char* restrict b);

#endif
