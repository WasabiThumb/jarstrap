#include <string.h>
#include <malloc.h>
#include <stdlib.h>
#include "io.h"
#include "path.h"
#include "alloca.h"
#include "debug.h"

io_shell io_shell_open(const char* cmd) {
    return (io_shell) popen(cmd, "r");
}

bool io_shell_read_line(io_shell shell, char *buf, int bufSize) {
    return fgets(buf, bufSize, (FILE*) shell) != NULL;
}

void io_shell_close(io_shell shell) {
    pclose((FILE*) shell);
}

io_dir io_dir_open(const char* path) {
#ifdef __linux
    return (io_dir) opendir(path);
#endif
#ifdef WIN32
    size_t len = strlen(path);
    if (len + 2 >= MAX_PATH) {
        ERR_FATAL(ERR_ILLEGAL);
    }
    io_dir dir = (io_dir) PTR_CHECK(malloc(sizeof(io_dir_t)));
    memcpy(dir->path, path, len); // NOLINT(bugprone-not-null-terminated-result)
    dir->path[len] = '\\';
    dir->path[len + 1] = '*';
    dir->path[len + 2] = (char) 0;
    dir->handle = FindFirstFileA(dir->path, &dir->findData);
    dir->useCurrentData = TRUE;
    dir->done = (char) (INVALID_HANDLE_VALUE == dir->handle);
    return dir;
#endif
}

#ifdef WIN32
const char* io_dir_read_win32(volatile io_dir dir, DWORD attr) {
    if (dir->done) return NULL;
    if (dir->useCurrentData) {
        dir->useCurrentData = FALSE;
        if (dir->findData.dwFileAttributes & attr) {
            return dir->findData.cFileName;
        }
    }

    WIN32_FIND_DATA fd;
    while (FindNextFileA(dir->handle, &dir->findData) != 0) {
        if (fd.dwFileAttributes & attr) return dir->findData.cFileName;
    }
    DWORD err = GetLastError();
    if (err != ERROR_NO_MORE_FILES) {
        ERR_FATAL(ERR_IO);
    }
    FindClose(dir->handle);
    dir->handle = INVALID_HANDLE_VALUE;
    return NULL;
}
#endif
#ifdef __linux
const char* io_dir_read_unix(io_dir dir, unsigned char type) {
    struct dirent* ent;
    while ((ent = readdir((DIR*) dir)) != NULL) {
        if (ent->d_type == type || ent->d_type == DT_UNKNOWN) return ent->d_name;
    }
    return NULL;
}
#endif

const char* io_dir_read_directory(io_dir dir) {
#ifdef __linux
    return io_dir_read_unix(dir, DT_DIR);
#endif
#ifdef WIN32
    return io_dir_read_win32(dir, FILE_ATTRIBUTE_DIRECTORY);
#endif
}

const char* io_dir_read_file(io_dir dir) {
#ifdef __linux
    return io_dir_read_unix(dir, DT_REG);
#endif
#ifdef WIN32
    return io_dir_read_win32(dir, FILE_ATTRIBUTE_NORMAL);
#endif
}

static const char DEL_FAIL[] = "Failed to delete file ";
void io_dir_delete_children_starting_with_not_equal(io_dir dir, const char* restrict path, const char* restrict startsWith, const char* restrict notEqual) {
    size_t startsWithSize = strlen(startsWith);
    if (startsWithSize < 1) return;
    size_t notEqualSize = strlen(notEqual);

    const char* child;
    size_t childSize;
    while ((child = io_dir_read_file(dir)) != NULL) {
        childSize = strlen(child);
        if (childSize < startsWithSize) continue;

        bool nonMatch = false;
        for (int i=0; i < startsWithSize; i++) {
            if (child[i] != startsWith[i]) {
                nonMatch = true;
                break;
            }
        }
        if (nonMatch) continue;

        if (childSize == notEqualSize) {
            nonMatch = true;
            for (int i=0; i < notEqualSize; i++) {
                if (child[i] != notEqual[i]) {
                    nonMatch = false;
                    break;
                }
            }
            if (nonMatch) continue;
        }

        const char* full = path_join(path, child);
        if (remove(full) != 0) {
            char* msg = (char*) alloca(strlen(full) + sizeof(DEL_FAIL));
            strcpy(msg, DEL_FAIL);
            strcpy(&msg[sizeof(DEL_FAIL) - 1], full);
            perror(msg);
            dealloca(msg);
        }
        free((void*) full);
    }
}

void io_dir_close(io_dir dir) {
#ifdef __linux
    closedir((DIR*) dir);
#endif
#ifdef WIN32
    if (INVALID_HANDLE_VALUE != dir->handle) FindClose(dir->handle);
    free((void*) dir);
#endif
}

#ifdef __linux
static const char ZENITY_CMD_A[] = "zenity --question --title '";
static const char ZENITY_CMD_B[] = "' --text '";
bool io_gui_question_zenity(const char* title, const char* question) {
    size_t titleLen = strlen(title);
    size_t questionLen = strlen(question);
    size_t size = sizeof(ZENITY_CMD_A) + sizeof(ZENITY_CMD_B) + titleLen + questionLen;

    char* cmd = (char*) PTR_CHECK(malloc(size));
    strcpy(cmd, ZENITY_CMD_A);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1], title);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1 + titleLen], ZENITY_CMD_B);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1 + titleLen + sizeof(ZENITY_CMD_B) - 1], question);
    cmd[size - 2] = '\'';
    cmd[size - 1] = (char) 0;

    FILE *fp = popen(cmd, "r");
    free(cmd);
    if (fp == NULL) {
        perror("Failed to launch zenity");
        return false;
    }

    return WEXITSTATUS(pclose(fp)) == 0;
}
#endif

#ifdef WIN32
bool io_gui_question_win32(const char* title, const char* question) {
    int v = MessageBoxA(NULL, (LPCTSTR) question, (LPCTSTR) title, (UINT) MB_YESNO | (UINT) MB_ICONINFORMATION);
    switch (v) {
        case 0:
            ERR_FATAL(ERR_ILLEGAL);
        case IDYES:
            return true;
        case IDNO:
            return false;
        default:
            fprintf(stderr, "Unknown message box result code %d\n", v);
            exit(1);
    }
}
#endif

bool io_gui_question(const char* title, const char* question) {
#ifdef __linux
    return io_gui_question_zenity(title, question);
#endif
#ifdef WIN32
    return io_gui_question_win32(title, question);
#endif
}

#ifdef __linux
const char* io_get_app_dir_unix() {
    struct passwd* pw = getpwuid(getuid());
    if (pw->pw_dir == NULL) return NULL;

    const char* dir = path_join(pw->pw_dir, ".jarstrap");
    if (dir == NULL) return NULL;

    io_dir ent = io_dir_open(dir);
    if (ent != NULL) {
        io_dir_close(ent);
        return dir;
    }

    if (mkdir(dir, 0700) == 0) {
        return dir;
    }
    perror("Failed to create app directory");
    free((void*) dir);
    return NULL;
}
#endif

#ifdef WIN32
const char* io_get_app_dir_win32() {
    TCHAR path[MAX_PATH];
    if (!SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_COMMON_APPDATA, NULL, SHGFP_TYPE_CURRENT, path))) {
        ERR_FATAL(ERR_IO);
    }

    const char* full = path_join((const char*) path, "Wasabi Codes\\JARStrap");
    if (full == NULL) return NULL;

    DWORD attrib = GetFileAttributes(full);
    if (attrib != (DWORD) INVALID_FILE_ATTRIBUTES && (attrib & (DWORD) FILE_ATTRIBUTE_DIRECTORY)) return full;
    if (SHCreateDirectoryExA(NULL, (LPCTSTR) full, NULL) == ERROR_SUCCESS) return full;
    free((void*) full);
    return NULL;
}
#endif

const char* io_get_app_dir() {
#ifdef __linux
    return io_get_app_dir_unix();
#endif
#ifdef WIN32
    return io_get_app_dir_win32();
#endif
}

void io_file_put_buffer(const char* path, void* buf, size_t len) {
    FILE* fd = fopen(path, "wb");
    if (fd == NULL) {
        ERR_FATAL(ERR_IO);
    }
    fwrite(buf, 1, len, fd);
    fclose(fd);
}

bool io_file_exists(const char* path) {
    return access(path, F_OK) == 0;
}

#ifdef WIN32
void io_path_to_short_name_win32(char** path) {
    if (path == NULL) return;
    DWORD len = (size_t) GetShortPathNameA((LPCSTR) *path, NULL, 0);

    char* buf = (char*) _malloca(len + 1);
    if (GetShortPathNameA((LPCSTR) *path, (LPSTR) buf, len) == 0) {
        ERR_PRINT(ERR_UNKNOWN);
        _freea(buf);
        return;
    }
    buf[len] = (char) 0;

    *path = (char*) realloc(*path, len + 1);
    strcpy(*path, buf);
    _freea(buf);
}

// static const wchar_t UNIQUE_TITLE_SEED[] = L"UniqueConsoleXXXXXXXX";
void io_init_console_win32(const char* title) {
    // wchar_t uniqueTitle[sizeof(UNIQUE_TITLE_SEED)];
    // memcpy(uniqueTitle, UNIQUE_TITLE_SEED, sizeof(UNIQUE_TITLE_SEED));
    // unsigned char rand[8];
    // RtlGenRandom((void*) rand, sizeof(unsigned char) * 8);
    // int z = 7;
    // for (int i=0; i < sizeof(UNIQUE_TITLE_SEED) - 1; i++) {
    //     if (uniqueTitle[i] == L'X') {
    //         uniqueTitle[i] = (wchar_t) ((rand[z--] % 26) + L'A'); // NOLINT(cert-msc50-cpp)
    //     }
    // }

    // SetConsoleTitleW(uniqueTitle);
    // Sleep((DWORD) 40);
    // HWND win = FindWindowW(NULL, uniqueTitle);
    SetConsoleTitleA(title);

    // if (win != NULL) {
    //    printf("Found HWND: %p\n", win);
    // }

    HANDLE h = GetStdHandle(STD_OUTPUT_HANDLE);
    if (h == NULL) {
        printf("Handle is null\n");
        return;
    }

    DWORD mode;
    if (GetConsoleMode(h, &mode) == 0) return;
    mode |= (DWORD) ENABLE_VIRTUAL_TERMINAL_PROCESSING;
    mode |= (DWORD) ENABLE_PROCESSED_OUTPUT;
    SetConsoleMode(h, mode);
}
#endif
