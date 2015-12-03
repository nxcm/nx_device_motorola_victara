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
#include <errno.h>
#include <string.h>
#include <fcntl.h>

#define LOG_TAG "PowerHAL"
#include <utils/Log.h>

#include <hardware/hardware.h>
#include <hardware/power.h>

#define CPUFREQ_PATH "/sys/devices/system/cpu/cpu0/cpufreq/"
#define CPUFREQ1_PATH "/sys/devices/system/cpu/cpu1/"
#define CPUFREQ2_PATH "/sys/devices/system/cpu/cpu2/"
#define CPUFREQ3_PATH "/sys/devices/system/cpu/cpu3/"
#define INTERACTIVE_PATH "/sys/devices/system/cpu/cpufreq/intelliactive/"
#define MSMHOTPLUG_PATH "/sys/module/msm_hotplug/"
#define GPU_PATH "/sys/class/kgsl/kgsl-3d0/devfreq/"
#define BOOST_PATH "/sys/module/cpu_boost/parameters/"

#define SCALING_MAX_FREQ "2457600"
#define SCALING_MAX_FREQ_LPM "1190400"

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

    if (on) {
        sysfs_write(MSMHOTPLUG_PATH "max_cpus_online", "4");
    } else {
        sysfs_write(MSMHOTPLUG_PATH "max_cpus_online", "2");
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

    if (profile == current_power_profile)
        return;

    switch (profile) {
    case PROFILE_BALANCED:
        sysfs_write(MSMHOTPLUG_PATH "max_cpus_online", "4");
        sysfs_write(GPU_PATH "governor", "msm-adreno-tz");
        sysfs_write(BOOST_PATH "sync_threshold", "1574400");
        sysfs_write(BOOST_PATH "input_boost_freq", "1190400");
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ);
        sysfs_write(CPUFREQ_PATH "scaling_min_freq", "268800");
        sysfs_write(CPUFREQ_PATH "scaling_governor", "intelliactive");
        ALOGD("%s: set balanced mode", __func__);
        break;
    case PROFILE_HIGH_PERFORMANCE:
        sysfs_write(MSMHOTPLUG_PATH "max_cpus_online", "4");
        sysfs_write(GPU_PATH "governor", "performance");
        sysfs_write(BOOST_PATH "sync_threshold", "2457600");
        sysfs_write(BOOST_PATH "input_boost_freq", "2457600");
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ);
        sysfs_write(CPUFREQ_PATH "scaling_min_freq", "2457600");
        sysfs_write(CPUFREQ_PATH "scaling_governor", "performance");
        sysfs_write(CPUFREQ1_PATH "online", "1");
        sysfs_write(CPUFREQ2_PATH "online", "1");
        sysfs_write(CPUFREQ3_PATH "online", "1");
        ALOGD("%s: set performance mode", __func__);
        break;
    case PROFILE_POWER_SAVE:
        sysfs_write(MSMHOTPLUG_PATH "max_cpus_online", "2");
        sysfs_write(GPU_PATH "governor", "powersave");
        sysfs_write(BOOST_PATH "sync_threshold", "1190400");
        sysfs_write(BOOST_PATH "input_boost_freq", "729600");
        sysfs_write(CPUFREQ_PATH "scaling_max_freq", SCALING_MAX_FREQ_LPM);
        sysfs_write(CPUFREQ_PATH "scaling_min_freq", "268800");
        sysfs_write(CPUFREQ_PATH "scaling_governor", "smartmax");
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
