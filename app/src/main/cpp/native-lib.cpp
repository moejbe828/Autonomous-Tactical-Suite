
#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_ats_sentinel_NativeRuntimeBridge_nativeStatus(
        JNIEnv* env,
        jobject /* thiz */) {

    std::string status =
        "ATS NATIVE BRIDGE READY - LLAMA CPP PENDING";

    return env->NewStringUTF(status.c_str());
}
