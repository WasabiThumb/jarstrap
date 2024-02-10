#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
#include "src/jre.h"
#include "src/io.h"
#include "src/version.h"
#include "src/archive.h"
#include "src/util.h"
#include "src/path.h"
#include "src/ansi.h"
#include "src/debug.h"

// CONFIG START
static const char APP_NAME[] = "JARStrap";
static const unsigned int MIN_JAVA_VERSION = 8;
static const unsigned int PREFERRED_JAVA_VERSION = 17;
static const char INSTALL_PROMPT[] = "This application requires Java %d or greater, which could not be found. Install now? The download may take a few moments.";
static const char LAUNCH_FLAGS[] = "";
// CONFIG END

static const char RUN_DELIMITER[] = "@==============@";
static const char JAR_EXT[] = ".jar";
#ifdef WIN32
static const char START_FMT[] = "%s -jar \"%s\" %s";
#endif
#ifdef __linux
static const char START_FMT[] = "\"%s\" -jar \"%s\" %s";
#endif

#ifdef NDEBUG
#pragma ide diagnostic ignored "EmptyDeclOrStmt"
#define printf_dbg(ignored, ...)
#else
#define printf_dbg printf
#endif


void startup() {
    bool nameGiven = util_fast_hash((const unsigned char*) APP_NAME, sizeof(APP_NAME)) != -1640289140; // Check if app name is not JARStrap

    printf(BLK CYNB " JARStrap %s (%d-bit) " CRESET "\n", version_get(), (int) (sizeof(void*) << 3));
    printf(CYN "A Java Archive to executable tool\n" CRESET);
    printf(BCYN "(c)" CYN " Wasabi Codes %s\n" CRESET, version_get_copyright_year());
    printf(CYN "https://wasabithumb.github.io/\n\n" CRESET);

    if (nameGiven) printf("Application Name :: %s\n", APP_NAME);
    printf_dbg("Minimum Java Version :: %d\n", MIN_JAVA_VERSION);
    printf_dbg("Preferred Java Version :: %d\n\n", PREFERRED_JAVA_VERSION);
}

void get_params(const char** outBinary, const char** outArchive, bool exitOnNotFound) {
    printf_dbg("Locating system installed Java...\n");
    const char* binary = jre_locate_at_least(MIN_JAVA_VERSION);
    if (binary == NULL) {
        if (exitOnNotFound) exit(0);
        printf_dbg("Java >=%d not found, showing install prompt\n", MIN_JAVA_VERSION);
        size_t promptSize = sizeof(INSTALL_PROMPT) + (sizeof(char) * 8);
        char* prompt = PTR_CHECK((char*) malloc(promptSize));
        snprintf(prompt, promptSize - 1, INSTALL_PROMPT, MIN_JAVA_VERSION);
        bool dl = io_gui_question(APP_NAME, prompt);
        free(prompt);
        if (dl) {
            if (jre_attempt_automated_install(MIN_JAVA_VERSION)) {
                get_params(outBinary, outArchive, true);
                return;
            }
            printf("Automated Java install not available, opening download page in browser\n");
            jre_open_download_page(PREFERRED_JAVA_VERSION);
        } else {
            printf_dbg("Prompt refused\n");
        }
        exit(0);
    }

    const char* ver = jre_version_get(binary);
    if (ver != NULL) {
        printf_dbg("Using Java %s at %s\n\n", ver, binary);
        free((void*) ver);
    } else {
        printf_dbg("Using Java at %s\n\n", binary);
    }

    unsigned char* archive;
    size_t archiveSize;
    archive_get(&archive, &archiveSize);

    printf_dbg("Computing archive hash...\n");

    uint32_t hash = util_fast_hash(archive, archiveSize);
    printf_dbg("Hash: %d\n", hash);

    char fName[8 + sizeof(JAR_EXT) + sizeof(APP_NAME)];
    memcpy(fName, APP_NAME, sizeof(APP_NAME) - 1);
    fName[sizeof(APP_NAME) - 1] = '_';
    util_uint2hex(hash, &fName[sizeof(APP_NAME)]);
    strcpy(&fName[sizeof(APP_NAME) + 8], JAR_EXT);
    char* appDir = (char*) io_get_app_dir();
    if (appDir == NULL) {
        ERR_FATAL(ERR_IO);
    }
    io_dir appDirEnt = io_dir_open(appDir);
    if (appDirEnt != NULL) {
        printf_dbg("Clearing any outdated binaries...\n");
        io_dir_delete_children_starting_with_not_equal(appDirEnt, appDir, APP_NAME, fName);
        io_dir_close(appDirEnt);
    }
    char* dest = (char*) path_join(appDir, fName);
    free(appDir);

    printf_dbg("Writing archive to %s\n", dest);
    if (io_file_exists(dest)) {
        printf_dbg("Already exists, skipping\n");
    } else {
        io_file_put_buffer(dest, archive, archiveSize);
    }
    printf_dbg("Ready to start application\n\n");

    *outBinary = binary;
    *outArchive = dest;
}

int do_cmd(char* cmd) {
    printf(GRN "%s\n" BGRN "%s\n\n" CRESET, cmd, RUN_DELIMITER);
    int stat = system(cmd);
    free(cmd);
    printf(BGRN "\n%s\n" CRESET, RUN_DELIMITER);

    if (stat == -1) {
        perror("Error when trying to run command");
        exit(1);
    }
#ifdef __linux
    stat = WEXITSTATUS(stat);
#endif
    printf("%sJava process finished with exit code %d\n" CRESET, stat == 0 ? YEL : RED, stat);
    return stat;
}

int main() {
#ifdef WIN32
    bool owns = io_owns_console_win32();
    HWND win = io_init_console_win32(APP_NAME, owns);
#endif
    startup();

    const char* binary;
    const char* archive;
    get_params(&binary, &archive, false);
#ifdef WIN32
    io_path_to_short_name_win32((char**) &binary);
#endif

    size_t cmdLen = strlen(binary) + strlen(archive) + sizeof(LAUNCH_FLAGS) + sizeof(START_FMT);
    char* cmd = PTR_CHECK(malloc(cmdLen));
    snprintf(cmd, cmdLen, START_FMT, binary, archive, LAUNCH_FLAGS);
    free((void*) binary);
    free((void*) archive);

#ifdef WIN32
    if (owns && win != NULL) {
        Sleep(200);
        if (CloseWindow(win) == 0) {
            ERR_PRINT(ERR_IO);
        }
    }
#endif
    return do_cmd(cmd);
}
