APP_PLATFORM := android-28
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_STL := c++_static
APP_CPPFLAGS := -std=gnu++11 -frtti -fexceptions -DANDROID_STL=c++_static

ifeq ($(NDK_DEBUG),0)
  APP_CPPFLAGS += -DNDEBUG
endif
