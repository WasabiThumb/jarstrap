
#ifndef JARSTRAP_IO_H
#define JARSTRAP_IO_H

#include <stdbool.h>
#include <stdio.h>
#ifdef WIN32

#include <io.h>
#define F_OK 0
#define access _access

#include <stdio.h>
#define popen _popen

#include <windows.h>
#include <shlobj.h>
// #include <ntsecapi.h>

typedef FILE* io_shell;
typedef struct io_dir_t {
    TCHAR path[MAX_PATH];
    WIN32_FIND_DATA findData;
    HANDLE handle;
    char done;
    char useCurrentData;
} io_dir_t;
typedef io_dir_t* io_dir;

#endif
#ifdef __linux

#include <unistd.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <pwd.h>

typedef FILE* io_shell;
typedef DIR* io_dir;

#endif

io_shell io_shell_open(const char* cmd);

bool io_shell_read_line(io_shell shell, char *buf, int bufSize);

void io_shell_close(io_shell shell);

io_dir io_dir_open(const char* path);

const char* io_dir_read_directory(io_dir dir);

const char* io_dir_read_file(io_dir dir);

void io_dir_delete_children_starting_with_not_equal(io_dir dir, const char* restrict path, const char* restrict startsWith, const char* restrict notEqual);

void io_dir_close(io_dir dir);

bool io_gui_question(const char* title, const char* question);

const char* io_get_app_dir();

void io_file_put_buffer(const char* path, void* buf, size_t len);

bool io_file_exists(const char* path);

#ifdef WIN32
void io_path_to_short_name_win32(char** path);

void io_init_console_win32(const char* title);
#endif

#endif
