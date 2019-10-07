LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifndef OPENCV_ANDROID_SDK
  $(C:\OpenCV-android-sdk)
endif

OPENCV_INSTALL_MODULES := on
include $(OPENCV_ANDROID_SDK)\sdk\native\jni\OpenCV.mk

ifndef EIGEN3_DIR
  $( C:\eigen-eigen-b3f3d4950030 )
endif

LOCAL_MODULE := face-lib
LOCAL_SRC_FILES += $(LOCAL_PATH)/face-lib.cpp $(LOCAL_PATH)/FaceRecognitionLib/Facebase.cpp
LOCAL_SRC_FILES += $(LOCAL_PATH)/FaceRecognitionLib/Eigenfaces.cpp $(LOCAL_PATH)/FaceRecognitionLib/Fisherfaces.cpp
LOCAL_SRC_FILES += $(LOCAL_PATH)/FaceRecognitionLib/PCA.cpp $(LOCAL_PATH)/FaceRecognitionLib/LDA.cpp
LOCAL_C_INCLUDES += $(EIGEN3_DIR)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/FaceRecognitionLib/RedSVD/include
LOCAL_LDLIBS += -llog -ldl
LOCAL_CPPFLAGS += -std=gnu++11 -frtti -fexceptions

include $(BUILD_SHARED_LIBRARY)
