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

// CONFIG START
static const char APP_NAME[] = "JARStrap";
static const unsigned int MIN_JAVA_VERSION = 8;
static const unsigned int PREFERRED_JAVA_VERSION = 17;
static const char INSTALL_PROMPT[] = "This application requires Java %d or greater, which could not be found. Install now?";
static const char LAUNCH_FLAGS[] = "";
// CONFIG END

static const char RUN_DELIMITER[] = "@==============@";
static const char JAR_EXT[] = ".jar";
static const char START_FMT[] = "\"%s\" -jar \"%s\" %s";
#ifdef NDEBUG
#pragma ide diagnostic ignored "EmptyDeclOrStmt"
#define printf_dbg(ignored, ...)
#else
#define printf_dbg printf
#endif


void startup() {
    bool nameGiven = util_fast_hash((const unsigned char*) APP_NAME, sizeof(APP_NAME)) != -1640289140; // Check if app name is not JARStrap

    printf("JARStrap %s (%d-bit)\n", version_get(), (int) (sizeof(void*) << 3));
    printf("A Java Archive to executable tool\n");
    printf("(c) Wasabi Codes %s\n", version_get_copyright_year());
    printf("https://wasabithumb.github.io/\n\n");

    if (nameGiven) printf("Application Name :: %s\n", APP_NAME);
    printf_dbg("Minimum Java Version :: %d\n", MIN_JAVA_VERSION);
    printf_dbg("Preferred Java Version :: %d\n\n", PREFERRED_JAVA_VERSION);
}

void get_params(const char** outBinary, const char** outArchive) {
    printf_dbg("Locating system installed Java...\n");
    const char* binary = jre_locate_at_least(MIN_JAVA_VERSION);
    if (binary == NULL) {
        printf_dbg("Java >=%d not found, showing install prompt\n", MIN_JAVA_VERSION);
        size_t promptSize = sizeof(INSTALL_PROMPT) + (sizeof(char) * 8);
        char* prompt = (char*) malloc(promptSize);
        if (prompt == NULL) {
            fprintf(stderr, "Out of memory (allocating buffer with capacity %zu)\n", promptSize);
            exit(1);
        }
        snprintf(prompt, promptSize - 1, INSTALL_PROMPT, MIN_JAVA_VERSION);
        bool dl = io_gui_question(APP_NAME, prompt);
        free(prompt);
        if (dl) {
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
        fprintf(stderr, "Failed to create app dir\n");
        exit(1);
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
    printf("%s\n%s\n\n", cmd, RUN_DELIMITER);
    int stat = system(cmd);
    free(cmd);
    printf("\n%s\n", RUN_DELIMITER);

    if (stat == -1) {
        perror("Error when trying to run command");
        exit(1);
    }
    stat = WEXITSTATUS(stat);
    printf("Java process finished with exit code %d\n", stat);
    return stat;
}

int main() {
    startup();

    const char* binary;
    const char* archive;
    get_params(&binary, &archive);

    size_t cmdLen = strlen(binary) + strlen(archive) + sizeof(LAUNCH_FLAGS) + sizeof(START_FMT);
    char* cmd = malloc(cmdLen);
    if (cmd == NULL) {
        fprintf(stderr, "Out of memory (allocating buffer with capacity %zu)\n", cmdLen);
        exit(1);
    }
    snprintf(cmd, cmdLen, START_FMT, binary, archive, LAUNCH_FLAGS);
    free((void*) binary);
    free((void*) archive);

    return do_cmd(cmd);
}
