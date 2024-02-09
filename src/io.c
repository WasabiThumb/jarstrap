#include <string.h>
#include <malloc.h>
#include <stdlib.h>
#include "io.h"
#include "path.h"
#include "alloca.h"

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
    return (io_dir) opendir(path);
}

const char* io_dir_read_directory(io_dir dir) {
    struct dirent* ent;
    while ((ent = readdir((DIR*) dir)) != NULL) {
        if (ent->d_type == DT_DIR || ent->d_type == DT_UNKNOWN) return ent->d_name;
    }
    return NULL;
}

const char* io_dir_read_file(io_dir dir) {
    struct dirent* ent;
    while ((ent = readdir((DIR*) dir)) != NULL) {
        if (ent->d_type == DT_REG || ent->d_type == DT_UNKNOWN) return ent->d_name;
    }
    return NULL;
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
    closedir((DIR*) dir);
}

#ifdef __linux
static const char ZENITY_CMD_A[] = "zenity --question --title '";
static const char ZENITY_CMD_B[] = "' --text '";
#endif
bool io_gui_question(const char* title, const char* question) {
    size_t titleLen = strlen(title);
    size_t questionLen = strlen(question);
    size_t size = sizeof(ZENITY_CMD_A) + sizeof(ZENITY_CMD_B) + titleLen + questionLen;
    void* ptr = malloc(size);
    if (ptr == NULL) {
        fprintf(stderr, "Out of memory (allocating buffer with capacity %zu)\n", size);
        exit(1);
    }

    char* cmd = (char*) ptr;
    strcpy(cmd, ZENITY_CMD_A);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1], title);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1 + titleLen], ZENITY_CMD_B);
    strcpy(&cmd[sizeof(ZENITY_CMD_A) - 1 + titleLen + sizeof(ZENITY_CMD_B) - 1], question);
    cmd[size - 2] = '\'';
    cmd[size - 1] = (char) 0;

    FILE *fp = popen(cmd, "r");
    free(ptr);
    if (fp == NULL) {
        perror("Failed to launch zenity");
        return false;
    }

    return WEXITSTATUS(pclose(fp)) == 0;
}

const char* io_get_app_dir() {
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

void io_file_put_buffer(const char* path, void* buf, size_t len) {
    FILE* fd = fopen(path, "wb");
    if (fd == NULL) {
        perror("Failed to write buffer to file");
        exit(1);
    }
    fwrite(buf, 1, len, fd);
    fclose(fd);
}

bool io_file_exists(const char* path) {
    return access(path, F_OK) == 0;
}
