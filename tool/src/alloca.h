
#ifndef JARSTRAP_ALLOCA_H
#define JARSTRAP_ALLOCA_H

#ifdef WIN32

#include <stdlib.h>
#include <malloc.h>
#ifndef alloca
#define alloca(s) _malloca(s)
#define dealloca(s) _freea(s)
#else
inline void dealloca(__attribute__((unused)) void* ptr) { }
#endif

#endif
#ifdef __linux

#include <alloca.h>
inline void dealloca(__attribute__((unused)) void* ptr) { }

#endif

#endif
