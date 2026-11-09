/* Copyright 2026 Amniscient. SPDX-License-Identifier: MIT */
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* Host glue. Helium's runtime wants a large native stack, so calls run on a
   dedicated thread. hr_init_globals and companion_exchange live in the
   generated C translation unit. */
int companion_exchange(const uint8_t *in, int64_t n, uint8_t *out, int64_t cap);

static pthread_mutex_t mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cv = PTHREAD_COND_INITIALIZER;
static pthread_cond_t done = PTHREAD_COND_INITIALIZER;
static pthread_t worker;
static int started = 0;
static int has_job = 0;
static const uint8_t *job_in = 0;
static int64_t job_in_n = 0;
static uint8_t *job_out = 0;
static int64_t job_cap = 0;
static int job_n = 0;

static void *worker_main(void *arg) {
    (void)arg;
    for (;;) {
        pthread_mutex_lock(&mu);
        while (!has_job) pthread_cond_wait(&cv, &mu);
        const uint8_t *in = job_in;
        int64_t in_n = job_in_n;
        uint8_t *out = job_out;
        int64_t cap = job_cap;
        pthread_mutex_unlock(&mu);
        int n = companion_exchange(in, in_n, out, cap > 0 ? cap - 1 : 0);
        if (n < 0) n = 0;
        if (cap > 0 && n >= cap) n = (int)cap - 1;
        if (out && n >= 0) out[n] = 0;
        pthread_mutex_lock(&mu);
        job_n = n;
        has_job = 0;
        pthread_cond_signal(&done);
        pthread_mutex_unlock(&mu);
    }
    return 0;
}

static void ensure_worker(void) {
    if (started) return;
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, (size_t)64 << 20);
    if (pthread_create(&worker, &at, worker_main, 0) != 0) started = -1;
    else started = 1;
    pthread_attr_destroy(&at);
}

JNIEXPORT jstring JNICALL
Java_com_amniscient_occompanion_HeliumBridge_handleNative(JNIEnv *env, jclass cls, jstring event) {
    (void)cls;
    const char *in = (*env)->GetStringUTFChars(env, event, 0);
    if (!in) return (*env)->NewStringUTF(env, "{\"error\":\"empty event\",\"credit\":\"Originally built by Amniscient\"}");
    const int64_t cap = 1024 * 1024;
    uint8_t *out = (uint8_t *)malloc((size_t)cap);
    if (!out) {
        (*env)->ReleaseStringUTFChars(env, event, in);
        return (*env)->NewStringUTF(env, "{\"error\":\"out of memory\",\"credit\":\"Originally built by Amniscient\"}");
    }
    int n = 0;
    pthread_mutex_lock(&mu);
    ensure_worker();
    if (started == 1) {
        job_in = (const uint8_t *)in;
        job_in_n = (int64_t)strlen(in);
        job_out = out;
        job_cap = cap;
        has_job = 1;
        pthread_cond_signal(&cv);
        while (has_job) pthread_cond_wait(&done, &mu);
        n = job_n;
    } else {
        n = companion_exchange((const uint8_t *)in, (int64_t)strlen(in), out, cap - 1);
        if (n < 0) n = 0;
        out[n] = 0;
    }
    pthread_mutex_unlock(&mu);
    jstring result = (*env)->NewStringUTF(env, (const char *)out);
    memset(out, 0, (size_t)n);
    free(out);
    (*env)->ReleaseStringUTFChars(env, event, in);
    return result;
}
