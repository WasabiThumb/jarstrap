#include <stdlib.h>
#include <malloc.h>
#include <string.h>
#include <stdbool.h>
#include "path.h"
#include "debug.h"


bool path_digest(char *output, size_t *cursor, const size_t *capacity, const char* input, bool leadWithSep) {
    int i = 0;
    char c;
    bool holdingDot = false;
    bool insertSep = leadWithSep;
    bool any = false;
    while ((c = input[i++]) != PATH_NULL) {
        if (*cursor >= *capacity) {
            ERR_FATAL(ERR_OVERFLOW);
        }
        if (insertSep) {
            output[(*cursor)++] = PATH_SEPARATOR;
            insertSep = false;
            i--;
            continue;
        }
        if (c == PATH_SEPARATOR) {
            insertSep = true;
            continue;
        }
        if (c == PATH_DOT) {
            if (holdingDot) {
                // double dot (..), traverse upwards
                if ((*cursor) == 0) {
                    output[(*cursor)++] = PATH_DOT;
                    output[(*cursor)++] = PATH_DOT;
                    continue;
                }
                bool sep2 = false;
                while ((*cursor)-- > 0) {
                    if (output[*cursor] == PATH_SEPARATOR) {
                        if (sep2) break;
                        sep2 = true;
                    }
                }
                holdingDot = false;
            } else {
                holdingDot = true;
            }
            continue;
        }
        if (holdingDot) {
            output[(*cursor)++] = '.';
            holdingDot = false;
            i--;
            continue;
        }
        any = true;
        output[(*cursor)++] = c;
    }
    return any;
}

const char* path_join(const char* restrict a, const char* restrict b) {
    if (a == NULL) {
        if (b == NULL) {
            return NULL;
        } else {
            return strdup(b);
        }
    } else if (b == NULL) {
        return strdup(a);
    }

    size_t al = strlen(a);
    if (al == 0) return strdup(b);
    size_t bl = strlen(b);
    if (bl == 0) return strdup(a);

    size_t capacity = al + bl + 4;
    char* ret = (char*) PTR_CHECK(malloc(capacity));
    size_t cursor = 0;

    bool lead = false;
    lead = path_digest(ret, &cursor, &capacity, a, lead);
    path_digest(ret, &cursor, &capacity, b, lead);
    ret[cursor] = PATH_NULL;

    return (const char*) realloc(ret, cursor + 1);
}
