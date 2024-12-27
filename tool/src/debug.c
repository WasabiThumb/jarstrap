#include "debug.h"
#include "ansi.h"


static const char ERR_TXT_UNKNOWN[] = "Unknown error";
static const char ERR_TXT_NOMEM[] = "Out of memory";
static const char ERR_TXT_ILLEGAL[] = "Illegal state";
static const char ERR_TXT_OVERFLOW[] = "Buffer overflow";
static const char ERR_TXT_IO[] = "I/O error";
#ifdef WIN32
static const char ERR_TXT_WWW[] = "Network error";
#endif

static const char ERR_PRINT_FMT[] = BRED "Error: %s @ %s:%d" CRESET;

const char* debug_err_text(debug_err_code code) {
    switch (code) {
        case ERR_NOMEM:
            return ERR_TXT_NOMEM;
        case ERR_ILLEGAL:
            return ERR_TXT_ILLEGAL;
        case ERR_OVERFLOW:
            return ERR_TXT_OVERFLOW;
        case ERR_IO:
            return ERR_TXT_IO;
#ifdef WIN32
        case ERR_WWW:
            return ERR_TXT_WWW;
#endif
        default:
            return ERR_TXT_UNKNOWN;
    }
}

void debug_err_print(debug_err_code code, const char* file, uint32_t line) {
    fprintf(stderr, ERR_PRINT_FMT, debug_err_text(code), file, line);
#ifdef __linux
    if (errno != 0) fprintf(stderr, " (%d)", errno);
#endif
#ifdef WIN32
    if (GetLastError() != 0) fprintf(stderr, " (%lu)", GetLastError());
#endif
    fprintf(stderr, "\n");
}
