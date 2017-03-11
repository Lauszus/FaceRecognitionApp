APP_PLATFORM := android-24
APP_ABI := armeabi-v7a arm64-v8a x86_64 x86
APP_STL := gnustl_static
APP_CPPFLAGS := -std=gnu++11 -frtti -fexceptions

ifeq ($(NDK_DEBUG),0)
  APP_CPPFLAGS += -DNDEBUG
endif
