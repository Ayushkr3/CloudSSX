#pragma once
#include "Global.h"
extern "C" VM_API void StartVM();
extern "C" VM_API void SendMouseInput(int delta_x, int delta_y, int button, bool pressed);
extern "C" VM_API void SendKeyboardInput(int key_code, bool pressed);
