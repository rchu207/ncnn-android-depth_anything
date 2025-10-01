// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/bitmap.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "dpt.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static Dpt* g_dpt = 0;
static ncnn::Mutex lock;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_dpt;
        g_dpt = 0;
    }
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_dpt_Dpt_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    if (modelid < 0 || modelid > 6 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "518",
        "vitsncnn",
        "vitbmetric",
    };

    const int target_sizes[] =
    {
        518,
        518,
        518,
    };

    const float mean_vals[][3] =
    {
        {123.675f, 116.28f,  103.53f},
        {123.675f, 116.28f,  103.53f},
        {123.675f, 116.28f,  103.53f},
    };

    const float norm_vals[][3] =
    {
        { 0.01712475f, 0.0175f, 0.01742919f },
        { 0.01712475f, 0.0175f, 0.01742919f },
        { 0.01712475f, 0.0175f, 0.01742919f },
    };

    const char* modeltype = modeltypes[(int)modelid];
    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_dpt;
            g_dpt = 0;
        }
        else
        {
            if (!g_dpt)
                g_dpt = new Dpt;
            g_dpt->load(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        }
    }

    return JNI_TRUE;
}

// public native boolean infer(Bitmap bitmap);
JNIEXPORT jboolean JNICALL Java_com_tencent_dpt_Dpt_infer(JNIEnv* env, jobject thiz, jobject bitmap)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "infer");

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;

    ncnn::MutexLockGuard g(lock);

    if (g_dpt == nullptr)
        return JNI_FALSE;

    // ncnn from bitmap
    int target_size = g_dpt->get_target_size();
    int width = info.width;
    int height = info.height;

    // pad to multiple of 32
    int w = width;
    int h = height;
    float scale = 1.f;
    if (w > h)
    {
        scale = (float)target_size / w;
        w = target_size;
        h = h * scale;
    }
    else
    {
        scale = (float)target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_BGR, w, h);
    ncnn::Mat depth_color;
    g_dpt->detect(in, w, h, depth_color);

    depth_color.to_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    return JNI_TRUE;
}

}
