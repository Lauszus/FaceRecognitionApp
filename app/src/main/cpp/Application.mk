APP_PLATFORM := android-24
APP_ABI := armeabi-v7a x86_64
APP_STL := gnustl_static
APP_CPPFLAGS := -std=gnu++11 -frtti -fexceptions

ifeq ($(NDK_DEBUG),1)
  APP_OPTIM := debug
else
  APP_CPPFLAGS += -DNDEBUG
  APP_OPTIM := release
endif
