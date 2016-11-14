LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES := on
include $(OPENCV_ANDROID_SDK)/sdk/native/jni/OpenCV.mk

LOCAL_MODULE := native-lib
LOCAL_SRC_FILES += native-lib.cpp
LOCAL_SRC_FILES += FaceRecognitionLib/Eigenfaces.cpp FaceRecognitionLib/Fisherfaces.cpp FaceRecognitionLib/PCA.cpp FaceRecognitionLib/LDA.cpp
LOCAL_C_INCLUDES += $(EIGEN3_DIR) $(LOCAL_PATH)/FaceRecognitionLib/RedSVD/include
LOCAL_LDLIBS += -llog -ldl
LOCAL_CPPFLAGS += -std=gnu++11 -frtti -fexceptions

include $(BUILD_SHARED_LIBRARY)
