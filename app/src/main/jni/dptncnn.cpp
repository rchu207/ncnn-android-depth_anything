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

//static void onImageAvailable(void* context, AImageReader* reader)
//{
//    // setup imagereader and its surface
////    AImageReader_new(640, 480, AIMAGE_FORMAT_YUV_420_888, /*maxImages*/2, &image_reader);
//
//    int32_t format;
//    AImage_getFormat(image, &format);
//
//    int32_t width = 0;
//    int32_t height = 0;
//    AImage_getWidth(image, &width);
//    AImage_getHeight(image, &height);
//
//    int32_t y_pixelStride = 0;
//    int32_t u_pixelStride = 0;
//    int32_t v_pixelStride = 0;
//    AImage_getPlanePixelStride(image, 0, &y_pixelStride);
//    AImage_getPlanePixelStride(image, 1, &u_pixelStride);
//    AImage_getPlanePixelStride(image, 2, &v_pixelStride);
//
//    int32_t y_rowStride = 0;
//    int32_t u_rowStride = 0;
//    int32_t v_rowStride = 0;
//    AImage_getPlaneRowStride(image, 0, &y_rowStride);
//    AImage_getPlaneRowStride(image, 1, &u_rowStride);
//    AImage_getPlaneRowStride(image, 2, &v_rowStride);
//
//    uint8_t* y_data = 0;
//    uint8_t* u_data = 0;
//    uint8_t* v_data = 0;
//    int y_len = 0;
//    int u_len = 0;
//    int v_len = 0;
//    AImage_getPlaneData(image, 0, &y_data, &y_len);
//    AImage_getPlaneData(image, 1, &u_data, &u_len);
//    AImage_getPlaneData(image, 2, &v_data, &v_len);
//
//    ((NdkCamera*)context)->on_image((unsigned char*)y_data, (int)width, (int)height);
//
//    const unsigned char* nv21 = y_data;
//    int nv21_width = width;
//    int nv21_height = height;
//
//    cv::Mat nv21_rotated(h + h / 2, w, CV_8UC1);
//    ncnn::kanna_rotate_yuv420sp(nv21, nv21_width, nv21_height, nv21_rotated.data, w, h, rotate_type);
//
//    // nv21_rotated to rgb
//    cv::Mat rgb(h, w, CV_8UC3);
//    ncnn::yuv420sp2rgb(nv21_rotated.data, w, h, rgb.data);
//
//    // crop and rotate nv21
//    cv::Mat nv21_croprotated(roi_h + roi_h / 2, roi_w, CV_8UC1);
//    {
//        const unsigned char* srcY = nv21 + nv21_roi_y * nv21_width + nv21_roi_x;
//        unsigned char* dstY = nv21_croprotated.data;
//        ncnn::kanna_rotate_c1(srcY, nv21_roi_w, nv21_roi_h, nv21_width, dstY, roi_w, roi_h, roi_w, rotate_type);
//
//        const unsigned char* srcUV = nv21 + nv21_width * nv21_height + nv21_roi_y * nv21_width / 2 + nv21_roi_x;
//        unsigned char* dstUV = nv21_croprotated.data + roi_w * roi_h;
//        ncnn::kanna_rotate_c2(srcUV, nv21_roi_w / 2, nv21_roi_h / 2, nv21_width, dstUV, roi_w / 2, roi_h / 2, roi_w, rotate_type);
//    }
//
//    // nv21_croprotated to rgb
//    cv::Mat rgb(roi_h, roi_w, CV_8UC3);
//    ncnn::yuv420sp2rgb(nv21_croprotated.data, roi_w, roi_h, rgb.data);
//
//    on_image_render(rgb);
//
//    // rotate to native window orientation
//    cv::Mat rgb_render(render_h, render_w, CV_8UC3);
//    ncnn::kanna_rotate_c3(rgb.data, roi_w, roi_h, rgb_render.data, render_w, render_h, render_rotate_type);
//
//    // scale to target size
//    if (buf.format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM || buf.format == AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM)
//    {
//        for (int y = 0; y < render_h; y++)
//        {
//            const unsigned char* ptr = rgb_render.ptr<const unsigned char>(y);
//            unsigned char* outptr = (unsigned char*)buf.bits + buf.stride * 4 * y;
//
//            int x = 0;
//#if __ARM_NEON
//            for (; x + 7 < render_w; x += 8)
//            {
//                uint8x8x3_t _rgb = vld3_u8(ptr);
//                uint8x8x4_t _rgba;
//                _rgba.val[0] = _rgb.val[0];
//                _rgba.val[1] = _rgb.val[1];
//                _rgba.val[2] = _rgb.val[2];
//                _rgba.val[3] = vdup_n_u8(255);
//                vst4_u8(outptr, _rgba);
//
//                ptr += 24;
//                outptr += 32;
//            }
//#endif // __ARM_NEON
//            for (; x < render_w; x++)
//            {
//                outptr[0] = ptr[0];
//                outptr[1] = ptr[1];
//                outptr[2] = ptr[2];
//                outptr[3] = 255;
//
//                ptr += 3;
//                outptr += 4;
//            }
//        }
//    }
//}

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
        "256",
    };

    const int target_sizes[] =
    {
        518,
        256,
    };

    const float mean_vals[][3] =
    {
        {123.675f, 116.28f,  103.53f},
        {123.675f, 116.28f,  103.53f},
    };

    const float norm_vals[][3] =
    {
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

// public native Bitmap infer(Bitmap bitmap);
JNIEXPORT jobject JNICALL Java_com_tencent_dpt_Dpt_infer(JNIEnv* env, jobject thiz, jobject bitmap)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "infer");

    ncnn::MutexLockGuard g(lock);

    double start_time = ncnn::get_current_time();

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return nullptr;

    if (g_dpt)
    {
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
        cv::Mat rgb;
        ncnn::Mat depth_color;
        g_dpt->detect(in, w, h, depth_color);

        depth_color.to_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);
    }

    return nullptr;
}

}
