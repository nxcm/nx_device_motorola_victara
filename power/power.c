/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdbool.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#define LOG_TAG "PowerHAL"
#include <utils/Log.h>

#include <hardware/hardware.h>
#include <hardware/power.h>

#define CPUFREQ_PATH "/sys/devices/system/cpu/cpu0/cpufreq/"
#define INTERACTIVE_PATH "/sys/devices/system/cpu/cpufreq/interactive/"

#define SCALING_MAX_FREQ "2457600"
#define SCALING_MAX_FREQ_LPM "1190400"

#define HISPEED_FREQ "1190400"
#define HISPEED_FREQ_LPM "998400"

#define GO_HISPEED_LOAD "50"
#define GO_HISPEED_LOAD_LPM "90"

#define TARGET_LOADS "80 1574400:90 2457600:99"
#define TARGET_LOADS_LPM "95 1190400:99"

enum {
    PROFILE_POWER_SAVE = 0,
    PROFILE_BALANCED,
    PROFILE_HIGH_PERFORMANCE,
    PROFILE_MAX
};

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int boostpulse_fd = -1;
static int current_power_profile = -1;
static int requested_power_profile = -1;
static int is_low_power_mode = 0;

static int sysfs_write(char *path, char *s)
{
    char buf[80];
    int len;
    int ret = 0;
    int fd;

    fd = open(path, O_WRONLY);
    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return -1 ;
    }

    len = write(fd, s, strlen(s));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);
        ret = -1;
    }

    close(fd);

    return ret;
}

static bool check_governor(void)
{
    struct stat s;
    int err = stat(INTERACTIVE_PATH, &s);
    if (err != 0) return false;
    if (S_ISDIR(s.st_mode)) return true;
    return false;
}

static void power_init(__attribute__((unused)) struct power_module *module)
{
    ALOGI("%s", __func__);
}

static int boostpulse_open()
{
    pthread_mutex_lock(&lock);
    if (boostpulse_fd < 0) {
        boostpulse_fd = open(INTERACTIVE_PATH "boostpulse", O_WRONLY);
    }
    pthread_mutex_unlock(&lock);

    return boostpulse_fd;
}

static void power_set_interactive(__attribute__((unused)) struct power_module *module, int on)
{
    if (current_power_profile != PROFILE_BALANCED)
        return;

    // break out early if governor is not interactive
    if (!check_governor()) return;

    if (on) {
        sysfs_write(INTERACTIVE_PATH "hispeed_freq", HISPEED_FREQ);
        sysfs_write(INTERACTIVE_PATH "go_hispeed_load", GO_HISPEED_LOAD);
        sysfs_write(INTERACTIVE_PATH "target_loads", TARGET_LOADS);
    } else {
        sysfs_write(INTERACTIVE_PATH "hispeed_freq", HISPEED_FREQ_LPM);
        sysfs_write(INTERACTIVE_PATH "go_hispeed_load", GO_HISPEED_LOAD_LPM);
        sysfs_write(INTERACTIVE_PATH "target_loads", TARGET_LOADS_LPM);
    }
}

static void set_power_profile(int profile)
{
    if (is_low_power_mode) {
        /* Let's assume we get a valid profile */
        requested_power_profile = profile;
        ALOGD("%s: low power mode enabled, ignoring profile change request", __func__);
        return;
    }

    if (!check_governor()) return;

    if (profile == current_power_profile)
        return;

    switch (profile) {
    case PROFILE_BALANCED:
        sysfs_write(INTERACTIVE_PATH "boost", "0");
        sysfs_write(INTERACTIVE_PATH "boostpulse_duration", "60000");
        sysfs_write(INTERACTIVE_PATH "go_hispeed_load", GO_HISPEED_LOAD);
        sysfs_write(INTERACTIVE_PATH "hispeed_freq", HISPEED_FREQ);
        sysfs_write(INTERACTIVE_PATH "io_is_busy", "1");
        sysfs_write(INTERACTIVE_PATH "min_sample_time", "60000");
        sysfs_write(INTERACTIVE_PATH "sampling_down_factor", "100000");
        sysfs_write(INTERACTIVE_PATH "target_loads", TARGET_LOADS);
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ);
        ALOGD("%s: set balanced mode", __func__);
        break;
    case PROFILE_HIGH_PERFORMANCE:
        sysfs_write(INTERACTIVE_PATH "boost", "1");
        sysfs_write(INTERACTIVE_PATH "boostpulse_duration", "60000");
        sysfs_write(INTERACTIVE_PATH "go_hispeed_load", GO_HISPEED_LOAD);
        sysfs_write(INTERACTIVE_PATH "hispeed_freq", HISPEED_FREQ);
        sysfs_write(INTERACTIVE_PATH "io_is_busy", "1");
        sysfs_write(INTERACTIVE_PATH "min_sample_time", "60000");
        sysfs_write(INTERACTIVE_PATH "sampling_down_factor", "100000");
        sysfs_write(INTERACTIVE_PATH "target_loads", "80");
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ);
        ALOGD("%s: set performance mode", __func__);
        break;
    case PROFILE_POWER_SAVE:
        sysfs_write(INTERACTIVE_PATH "boost", "0");
        sysfs_write(INTERACTIVE_PATH "boostpulse_duration", "0");
        sysfs_write(INTERACTIVE_PATH "go_hispeed_load", GO_HISPEED_LOAD_LPM);
        sysfs_write(INTERACTIVE_PATH "hispeed_freq", HISPEED_FREQ_LPM);
        sysfs_write(INTERACTIVE_PATH "io_is_busy", "0");
        sysfs_write(INTERACTIVE_PATH "min_sample_time", "60000");
        sysfs_write(INTERACTIVE_PATH "sampling_down_factor", "100000");
        sysfs_write(INTERACTIVE_PATH "target_loads", TARGET_LOADS_LPM);
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ_LPM);
        ALOGD("%s: set powersave", __func__);
        break;
    default:
        ALOGE("%s: unknown profile: %d", __func__, profile);
        return;
    }

    current_power_profile = profile;
}

static void set_low_power_mode(int on)
{
    if (on == is_low_power_mode)
        return;

    ALOGD("%s: state=%d", __func__, on);

    if (on) {
        requested_power_profile = current_power_profile;
        set_power_profile(PROFILE_POWER_SAVE);
        is_low_power_mode = 1;
    } else {
        is_low_power_mode = 0;
        set_power_profile(requested_power_profile);
    }
}

static void power_hint( __attribute__((unused)) struct power_module *module,
                        __attribute__((unused)) power_hint_t hint,
                        __attribute__((unused)) void *data)
{
    char buf[80];
    int len;

    switch (hint) {
    case POWER_HINT_INTERACTION:
        if (current_power_profile != PROFILE_BALANCED)
            return;

        // break out early if governor is not interactive
        if (!check_governor()) return;

        if (boostpulse_open() >= 0) {
            snprintf(buf, sizeof(buf), "%d", 1);
            len = write(boostpulse_fd, &buf, sizeof(buf));
            if (len < 0) {
                strerror_r(errno, buf, sizeof(buf));
                ALOGE("Error writing to boostpulse: %s\n", buf);

                pthread_mutex_lock(&lock);
                close(boostpulse_fd);
                boostpulse_fd = -1;
                pthread_mutex_unlock(&lock);
            }
        }
        break;
    case POWER_HINT_SET_PROFILE:
        pthread_mutex_lock(&lock);
        set_power_profile(*(int32_t *)data);
        pthread_mutex_unlock(&lock);
        break;
    case POWER_HINT_LOW_POWER:
        pthread_mutex_lock(&lock);
        set_low_power_mode(*(int32_t *)data ? 1 : 0);
        pthread_mutex_unlock(&lock);
        break;
    default:
        break;
    }
}

static struct hw_module_methods_t power_module_methods = {
    .open = NULL,
};

static int get_feature(__attribute__((unused)) struct power_module *module,
                       feature_t feature)
{
    if (feature == POWER_FEATURE_SUPPORTED_PROFILES) {
        return PROFILE_MAX;
    }
    return -1;
}

struct power_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = POWER_MODULE_API_VERSION_0_2,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = POWER_HARDWARE_MODULE_ID,
        .name = "msm8974 Power HAL",
        .author = "The Nexus Experience Project",
        .methods = &power_module_methods,
    },

    .init = power_init,
    .setInteractive = power_set_interactive,
    .powerHint = power_hint,
    .getFeature = get_feature
};
