#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/wait.h>
#include <sys/poll.h>

/* execveat wrapper for aarch64 */
#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif
#ifndef __NR_execveat
#define __NR_execveat 387
#endif

static int do_execveat(int fd, const char *path, char *const argv[], char *const envp[], int flags) {
    return syscall(__NR_execveat, fd, path, argv, envp, flags);
}

/* Check if binary can be executed (returns 0 on success, -errno on failure) */
JNIEXPORT jint JNICALL
Java_com_tcc_TermuxBootstrap_nativeExecCheck(JNIEnv *env, jclass cls, jstring path) {
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) return -EINVAL;

    int fd = open(cpath, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return -errno;
    }

    /* Build argv for a simple test: /path/to/bash -c "exit 0" */
    char *argv[] = {(char*)cpath, "-c", "exit 0", NULL};

    pid_t pid = fork();
    if (pid == 0) {
        /* Child: try execveat first, then execve */
        do_execveat(fd, "", argv, environ, AT_EMPTY_PATH);
        execve(cpath, argv, environ);
        _exit(127);
    }
    if (pid < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, cpath);
        return -errno;
    }

    /* Parent: wait for child */
    int status;
    waitpid(pid, &status, 0);
    close(fd);
    (*env)->ReleaseStringUTFChars(env, path, cpath);

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) return 0;
    if (WIFEXITED(status)) return -WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -1;
    return -1;
}

/* Full exec: returns child PID, or negative errno */
JNIEXPORT jint JNICALL
Java_com_tcc_TermuxBootstrap_nativeExec(JNIEnv *env, jclass cls,
    jstring path, jobjectArray args) {
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) return -EINVAL;

    int argc = args ? (*env)->GetArrayLength(env, args) : 0;
    char **argv = malloc((argc + 2) * sizeof(char*));
    argv[0] = strdup(cpath);
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char *cs = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i+1] = strdup(cs);
        (*env)->ReleaseStringUTFChars(env, s, cs);
    }
    argv[argc+1] = NULL;

    int fd = open(cpath, O_RDONLY | O_CLOEXEC);

    pid_t pid = fork();
    if (pid == 0) {
        if (fd >= 0) {
            do_execveat(fd, "", argv, environ, AT_EMPTY_PATH);
            close(fd);
        }
        execve(cpath, argv, environ);
        _exit(127);
    }

    if (fd >= 0) close(fd);
    for (int i = 0; i <= argc; i++) free(argv[i]);
    free(argv);
    (*env)->ReleaseStringUTFChars(env, path, cpath);

    if (pid < 0) return -errno;
    return pid;
}
