#include <stdlib.h>
#include <malloc.h>
#include <string.h>
#include "io.h"
#include "alloca.h"
#include "path.h"
#include "jre.h"
#include "util.h"

#ifdef __linux
#include <sys/utsname.h>
#include <ctype.h>
#endif
#ifdef WIN32
#include <windows.h>
#include <versionhelpers.h>
#include <winhttp.h>
#endif

// CONSTANTS
#ifdef __linux
static const char JRE_EXEC[] = "java";
static const char JRE_ARG_VERSION[] = " -version 2>&1";
static const char JRE_KNOWN_LOCATIONS[] = "/usr/lib/jvm";
static const char JRE_DOWNLOAD_CMD[] = "xdg-open \"https://adoptium.net/temurin/releases/?package=jre&os=linux&version=";
static const char JRE_DOWNLOAD_CMD_ALPINE[] = "xdg-open \"https://adoptium.net/temurin/releases/?package=jre&os=alpine-linux&version=";
static const char JRE_DOWNLOAD_CMD_ARCH[] = "xdg-open \"https://archlinux.org/packages/?sort=&q=java-runtime-openjdk%3D";
#define JRE_DOWNLOAD_CMD_ESCAPE
#endif
#ifdef WIN32
static const char JRE_EXEC[] = "java.exe";
static const char JRE_ARG_VERSION[] = " -version 2>&1";
static const char JRE_KNOWN_LOCATIONS[] = "C:\\Program Files\\Java:C:\\Program Files (x86)\\Java";
static const char JRE_DOWNLOAD_CMD[] = "https://adoptium.net/temurin/releases/?package=jre&os=windows&version=";
static const wchar_t JRE_LATEST_MSI_HOST[] = L"github.com";
static const wchar_t JRE_LATEST_MSI_OBJECT[] = L"/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jre_x64_windows_hotspot_21.0.2_13.msi";
static const wchar_t JRE_LATEST_MSI_MIME[] = L"application/x-ms-installer";
#endif
static const char JRE_ARG_VERSION_PREFIX[] = "version \"";

// INTERNAL
char* jre_locate_at_least000(char* head, int depth) {
    depth--;

    char* ret = (char*) path_join(head, "bin");
    io_dir test = io_dir_open(ret);
    if (test != NULL) {
        io_dir_close(test);
        free(head);
        return ret;
    }
    free(ret);

    if (depth > 0) {
        io_dir dir = io_dir_open(head);
        if (dir == NULL) {
            free(head);
            return NULL;
        }

        const char* sub;
        while ((sub = io_dir_read_directory(dir)) != NULL) {
            ret = jre_locate_at_least000((char*) path_join(head, sub), depth);
            if (ret != NULL) {
                io_dir_close(dir);
                free(head);
                return ret;
            }
        }

        io_dir_close(dir);
    }

    free(head);
    return NULL;
}

const char* jre_locate_at_least00(unsigned int min, char* known) {
    if (!io_file_exists(known)) return NULL;

    io_dir dir = io_dir_open(known);
    if (dir == NULL) return NULL;
    const char* sub;
    while ((sub = io_dir_read_directory(dir)) != NULL) {
        if (sub[0] == '.') continue;
        char* exec = jre_locate_at_least000((char*) path_join(known, sub), 3);
        size_t orig = strlen(exec);
        exec = (char*) realloc(exec, orig + 1 + sizeof(JRE_EXEC));
        exec[orig] = PATH_SEPARATOR;
        strcpy(&exec[orig + 1], JRE_EXEC);

        const char* ver = jre_version_get(exec);
        if (ver != NULL) {
            bool atl = jre_version_at_least(ver, min);
            free((void*) ver);
            if (atl) {
                io_dir_close(dir);
                return exec;
            }
        }
        free((void*) exec);
    }
    io_dir_close(dir);

    return NULL;
}

const char* jre_locate_at_least0(unsigned int min, char* known, int len) {
    char* next = NULL;
    int nextLen = 0;
    for (int i=0; i < len; i++) {
        if (known[i] == ':') {
            known[i] = (char) 0;
            i++;
            next = &known[i];
            nextLen = len - i;
            break;
        }
    }

    const char* ret = jre_locate_at_least00(min, known);
    if (ret != NULL) return ret;
    if (nextLen) return jre_locate_at_least0(min, next, nextLen);
    return NULL;
}

#ifdef __linux
void jre_refine_download_cmd_linux(const char** cmd, size_t* size) {
    void* ptr = malloc(sizeof(struct utsname));
    if (ptr == NULL) {
        fprintf(stderr, "Out of memory (cannot allocate buffer with size %d)\n", (int) sizeof(struct utsname));
        exit(1);
    }
    struct utsname *dat = (struct utsname*) ptr;
    if (uname(dat) != 0) {
        perror("Failed to call uname, distro specific action may not be taken");
        return;
    }
    int sysnameLen = (int) strlen(dat->sysname);
    int releaseLen = (int) strlen(dat->release);
    int haystackLen = sysnameLen + releaseLen + 2;
    char* haystack = (char*) alloca(haystackLen);
    for (int i=0; i < sysnameLen; i++) haystack[i] = tolower(dat->sysname[i]); // NOLINT(cert-str34-c)
    for (int i=0; i < releaseLen; i++) haystack[sysnameLen + 1 + i] = tolower(dat->release[i]); // NOLINT(cert-str34-c)
    haystack[sysnameLen] = ' ';
    haystack[haystackLen - 1] = (char) 0;
    free(ptr);

    if (strstr(haystack, "alpine") != NULL) {
        *cmd = JRE_DOWNLOAD_CMD_ALPINE;
        *size = sizeof(JRE_DOWNLOAD_CMD_ALPINE);
    } else if (strstr(haystack, "arch") != NULL || strstr(haystack, "manjaro") != NULL) {
        *cmd = JRE_DOWNLOAD_CMD_ARCH;
        *size = sizeof(JRE_DOWNLOAD_CMD_ARCH);
    }
    dealloca(haystack);
}
#endif

// API
const char* jre_version_get(const char* binary) {
    size_t binary_len = strlen(binary);
    char* exec = alloca(binary_len + sizeof(JRE_ARG_VERSION) + 3);
    exec[0] = '"';
    strcpy(&exec[1], binary);
    exec[binary_len + 1] = '"';
    strcpy(&exec[binary_len + 2], JRE_ARG_VERSION);

    char* version = NULL;

    io_shell shell;
    shell = io_shell_open(exec);
    dealloca(exec);
    if (shell == NULL) {
        fprintf(stderr, "Failed to run command\n");
        exit(1);
    }
    int bufSize = 128;
    void* bufPtr = malloc(bufSize);
    if (bufPtr == NULL) {
        fprintf(stderr, "Out of memory (cannot allocate buffer with size %d)\n", bufSize);
        exit(1);
    }
    char* buf = (char*) bufPtr;
    if (io_shell_read_line(shell, buf, bufSize)) {
        int len = (int) strlen(buf);
        int prefixHead = 0;
        int start = 0;
        int end = len - 1;
        bool found = false;
        char c;

        for (int i=0; i < len; i++) {
            c = buf[i];
            if (c == '\n') break;
            if (found) {
                if (c == '\"') {
                    end = i;
                    break;
                }
                continue;
            }
            if (c == JRE_ARG_VERSION_PREFIX[prefixHead]) {
                prefixHead++;
                if (prefixHead == sizeof(JRE_ARG_VERSION_PREFIX) - 1) {
                    start = i + 1;
                    if (start < len) found = true;
                }
            } else {
                prefixHead = 0;
            }
        }

        if (found) {
            buf[end] = (char) 0;
            int lowSize = end - start + 1;
            if (start >= lowSize) {
                memcpy(buf, &buf[start], lowSize * sizeof(char));
            } else {
                memmove(buf, &buf[start], lowSize * sizeof(char));
            }
            version = (char*) realloc(bufPtr, lowSize);
        } else {
            free(buf);
        }
    } else {
        free(buf);
    }
    io_shell_close(shell);

    return version;
}

bool jre_version_at_least(const char* version, unsigned int min) {
    if (version == NULL) return false;
    size_t versionLen = strlen(version);
    if (versionLen < 1) return false; // Bad input

    if (version[0] == '9') return min <= 9;
    if (versionLen < 2) return false; // Bad input
    if (version[0] == '1') {
        if (version[1] == '.') {
            if (versionLen < 3) return false; // Bad input
            if (min > 8) return false;
            int minor = (int) (version[2] - '0');
            return min <= minor;
        }
        if (min > 19) return false;
        if (min < 11) return true;
    } else if (version[0] > '1' && version[0] <= '9') {
        unsigned int hi = (unsigned int) (version[0] - '0');
        unsigned int hiTarget = min / 10;

        if (hiTarget > hi) return false;
        if (hiTarget < hi) return true;
    } else {
        return false; // Bad input
    }

    int lo = (int) (version[1] - '0');
    return (min % 10) <= lo;
}

#define INT_RV(v) const char* ret = path_join((v), JRE_EXEC); if (access(ret, F_OK) == 0) { free(builder); return ret; } free((void*) ret)
#ifdef __linux
#define PATH_VAR_SEP ':'
#endif
#ifdef WIN32
#define PATH_VAR_SEP ';'
#endif
const char* jre_locate_path() {
    char* path = getenv("PATH");
    if (path == NULL) {
        fprintf(stderr, "PATH is null\n");
    } else {
        int builderCapacity = 64;
        char* builder = (char*) allocarray(sizeof(char), builderCapacity);
        if (builder == NULL) {
            fprintf(stderr, "Out of memory (cannot allocate buffer with size %d)\n", builderCapacity);
            exit(1);
        }
        int builderPosition = 0;
        int position = 0;
        int lastSegment = 0;

        char c;
        while ((c = path[position++]) != (char) 0) {
            if (builderPosition >= builderCapacity) {
                builderCapacity <<= 1;
                builder = (char*) reallocarray(builder, sizeof(char), builderCapacity);
                if (builder == NULL) {
                    fprintf(stderr, "Out of memory (cannot allocate buffer with size %d)\n", builderCapacity);
                    exit(1);
                }
            }
            if (c == PATH_VAR_SEP) {
                builder[builderPosition] = (char) 0;
                INT_RV(builder);
                builderPosition = 0;
                lastSegment = position;
            } else {
                builder[builderPosition++] = c;
            }
        }

        INT_RV(&builder[lastSegment]);
        free(builder);
    }
    return NULL;
}

const char* jre_locate_at_least(unsigned int min) {
    const char* path = jre_locate_path();
    if (path != NULL) {
        const char* version = jre_version_get(path);
        if (version != NULL) {
            bool atLeast = jre_version_at_least(version, min);
            free((void*) version);
            if (atLeast) return path;
        }
        free((void*) path);
    }

    char* known = malloc(sizeof(JRE_KNOWN_LOCATIONS));
    if (known == NULL) {
        fprintf(stderr, "Out of memory (cannot allocate buffer with size %d)\n", (int) sizeof(JRE_KNOWN_LOCATIONS));
        exit(1);
    }
    memcpy(known, JRE_KNOWN_LOCATIONS, sizeof(JRE_KNOWN_LOCATIONS));
    const char* ret = jre_locate_at_least0(min, known, (int) (sizeof(JRE_KNOWN_LOCATIONS) - 1));
    free((void*) known);
    return ret;
}

void jre_open_download_page(unsigned int version) {
    unsigned int ltsVersion;
    int twoDigit = 1;
    if (version <= 8) {
        ltsVersion = 8;
        twoDigit = 0;
    } else if (version <= 11) {
        ltsVersion = 11;
    } else if (version <= 17) {
        ltsVersion = 17;
    } else if (version <= 21) {
        ltsVersion = 21;
    } else {
        ltsVersion = version;
    }

    const char* baseCmd = JRE_DOWNLOAD_CMD;
    size_t baseCmdSize = sizeof(JRE_DOWNLOAD_CMD);
#ifdef linux
    jre_refine_download_cmd_linux(&baseCmd, &baseCmdSize);
#endif

#ifdef JRE_DOWNLOAD_CMD_ESCAPE
    size_t size = baseCmdSize + twoDigit + 2;
#else
    size_t size = baseCmdSize + twoDigit + 1;
#endif
    void* ptr = alloca(size);

    char* cmd = (char*) ptr;
    strcpy(cmd, baseCmd);

    if (twoDigit) {
        cmd[baseCmdSize - 1] = (char) ((ltsVersion / 10) + '0');
        cmd[baseCmdSize] = (char) ((ltsVersion % 10) + '0');
    } else {
        cmd[baseCmdSize - 1] = '8';
    }

#ifdef JRE_DOWNLOAD_CMD_ESCAPE
    cmd[size - 2] = '\"';
#endif
    cmd[size - 1] = (char) 0;

#ifdef WIN32
    ShellExecute(NULL, "open", cmd, NULL, NULL, SW_SHOWNORMAL);
#else
    system(cmd);
#endif
    dealloca(ptr);
}

#ifdef WIN32
HINTERNET jre_open_msi_download_win32() {
    HINTERNET http = WinHttpOpen(L"JARStrap",
                                 IsWindows8Point1OrGreater() ? WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY : WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                 WINHTTP_NO_PROXY_NAME,
                                 WINHTTP_NO_PROXY_BYPASS,
                                 (DWORD) 0);
    if (http == NULL) {
        fprintf(stderr, "Error %lu in WinHttpOpen\n", GetLastError());
        return NULL;
    }

    http = WinHttpConnect(http, JRE_LATEST_MSI_HOST, INTERNET_DEFAULT_HTTPS_PORT, (DWORD) 0);
    if (http == NULL) {
        fprintf(stderr, "Error %lu in WinHttpConnect\n", GetLastError());
        return NULL;
    }

    LPCWSTR accepts[2] = { JRE_LATEST_MSI_MIME, NULL };
    http = WinHttpOpenRequest(http, L"GET", JRE_LATEST_MSI_OBJECT, NULL, WINHTTP_NO_REFERER, accepts, WINHTTP_FLAG_SECURE);
    if (http == NULL) {
        fprintf(stderr, "Error %lu in WinHttpOpenRequest\n", GetLastError());
        return NULL;
    }

    if (!WinHttpSendRequest(http, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0)) {
        WinHttpCloseHandle(http);
        fprintf(stderr, "Error %lu in WinHttpSendRequest\n", GetLastError());
        return NULL;
    }

    if (!WinHttpReceiveResponse(http, NULL)) {
        WinHttpCloseHandle(http);
        fprintf(stderr, "Error %lu in WinHttpReceiveResponse\n", GetLastError());
        return NULL;
    }
    return http;
}

bool jre_pipe_inet2fp_win32(HINTERNET inet, FILE* fp) {
    const DWORD buf_size = 8192;
    void* buf = malloc(buf_size);
    if (buf == NULL) {
        fprintf(stderr, "Out of memory (allocating buffer with capacity %lu)\n", buf_size);
        exit(1);
    }

    DWORD shovel;
    DWORD downloaded;
    do {
        shovel = 0;
        if (!WinHttpQueryDataAvailable(inet, &shovel)) {
            fprintf(stderr, "Error %lu in WinHttpQueryDataAvailable\n", GetLastError());
            free(buf);
            return false;
        }
        if (shovel > buf_size) shovel = buf_size;

        if (!WinHttpReadData(inet, buf, shovel, &downloaded)) {
            fprintf(stderr, "Error %lu in WinHttpReadData\n", GetLastError());
            free(buf);
            return false;
        }
        fwrite(buf, 1, (size_t) downloaded, fp);
    } while (shovel > 0);

    free(buf);
    return true;
}
#endif

bool jre_attempt_automated_install(unsigned int min) {
    bool ret = false;
    if (min > 21) return ret;
    if (sizeof(void*) != 8) return ret;
#ifdef WIN32
    const char* appDir = io_get_app_dir();
    if (appDir == NULL) {
        fprintf(stderr, "Cannot create app dir, automated install aborted\n");
        return false;
    }
    const char* dest = path_join(appDir, "install_jre.msi");
    free((void*) appDir);

    FILE* dh = fopen(dest, "w+b");
    if (dh == NULL) {
        perror("Failed to open file for writing");
        free((void*) dest);
        return false;
    }

    HINTERNET http = jre_open_msi_download_win32();
    if (http == NULL) {
        fclose(dh);
        free((void*) dest);
        return false;
    }

    printf("Downloading Java installer...\n");
    bool piped = jre_pipe_inet2fp_win32(http, dh);
    WinHttpCloseHandle(http);
    fclose(dh);
    if (!piped) {
        free((void *) dest);
        return false;
    }

    io_path_to_short_name_win32((char**) &dest);
    printf("Launching installer (%s)\n", dest);
    int out = system(dest);

    switch (out) { // NOLINT(hicpp-multiway-paths-covered)
        case 0:
            printf("Install successful\n");
            ret = true;
            break;
        case 1602:
            printf("Install cancelled by user\n");
            ret = true;
            break;
        case 1603:
            printf("Fatal error during install\n");
    }

    remove(dest);
    free((void *) dest);
#endif
    return ret;
}
