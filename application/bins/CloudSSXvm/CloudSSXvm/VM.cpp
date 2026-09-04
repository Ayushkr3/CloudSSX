#include "pch.h"
#include <iostream>
#include <fstream>
#include <vector>
#include <cstdint>
#include <cstring>
#include <Windows.h>
#include "io.h"
#include "memory.h"
#include "CPU.h"
#include <thread>
#include "PIC.h"
#include "PCI.h"
#include "Whpx.h"
#include "VM.h"
#include "Display.h"
WHPX* hypervisior;
PCISystemBus pci;
DisplayAdapter* Display;
static std::vector<uint8_t> load_bios(const char* path) {
	std::ifstream f(path, std::ios::binary | std::ios::ate);
	if (!f) {
		exit(1);
	}
	size_t sz = f.tellg();
	f.seekg(0);
	std::vector<uint8_t> buf(sz);
	f.read(reinterpret_cast<char*>(buf.data()), sz);
	return buf;
}
void EmulationLoop() {
	hypervisior->RunVP();
}
//void Poll() {
//	SDL_Event event;
//	while (running.load()) {
//		while (SDL_PollEvent(&event)) {
//			if (event.type == SDL_EVENT_QUIT) {
//				running = false;
//				hypervisior->StopVP();
//			}
//			else if (event.type == SDL_EVENT_WINDOW_FOCUS_GAINED) {
//				SDL_SetWindowRelativeMouseMode(Display->window, true);
//			}
//			else if (event.type == SDL_EVENT_WINDOW_FOCUS_LOST) {
//				SDL_SetWindowRelativeMouseMode(Display->window, false);
//			}
//			
//		}
//	}
//}
void StartVM() {
	const char* bios_path = "D:/windows nt/bios_nd.bin";
	const char* vgabios_path = "D:/windows nt/vgabios.bin";
	std::string isopath ="";
	auto bios = load_bios(bios_path);
	auto vgabios = load_bios(vgabios_path);
	fw_cfg.init(RAM_SIZE);
	InitMemory();
	Display = new DisplayAdapter;
	InitIO(&pci, WHPX::ThunkRaiseInterrupt, isopath);
	size_t offset = BIOS_SIZE - bios.size();
	memcpy((char*)RAM + BIOS_BASE + offset, bios.data(), bios.size());
	memcpy((char*)VGA_ROM, vgabios.data(), vgabios.size());
	ud.pic = &pic;
	ud.sb = &pci;
	
	Display->vgaC = ud.sb->vgaC;
	Display->vgaC->vga->adapter = Display;
	hypervisior = new WHPX(&ud);
	ud.whpxCtx = hypervisior;
	std::thread emulation(EmulationLoop);
	std::thread Time(Timers::TimerThread);
	//std::thread DisplayL(DisplayAdapter::DisplayThunkUpdateLoop,Display);
	//Poll();
	//DisplayL.join();
	Time.join();
	emulation.join();
	delete hypervisior;
	delete Display;
	DeinitMemory();
}

void SendMouseInput(int delta_x, int delta_y, int button, bool pressed)
{
	if (ud.whpxCtx == nullptr) {
		//guard rail if hypervisior is not yet created
		return;
	}
	if (button < 0) {
		ud.kbd->mouse.send_delta(delta_x, delta_y);
	}
	else {
		bool left = ud.kbd->mouse.left_btn;
		bool middle = ud.kbd->mouse.middle_btn;
		bool right = ud.kbd->mouse.right_btn;
		switch (button) {
		case 0: left = pressed; break;
		case 1: middle = pressed; break;
		case 2: right = pressed; break;
		default: return;
		}
		ud.kbd->mouse.left_btn = left;
		ud.kbd->mouse.middle_btn = middle;
		ud.kbd->mouse.right_btn = right;
		ud.kbd->mouse.send_click(left, middle, right);
	}
	ud.kbd->flush_mouse_output();
}

void SendKeyboardInput(int key_code, bool pressed)
{
	if (ud.whpxCtx == nullptr || ud.kbd == nullptr) {
		return;
	}

	struct KeyMapping {
		int key_code;
		uint8_t scancode;
		bool extended;
	};
	static const KeyMapping keymap[] = {
		{ 8, 0x0E, false }, { 9, 0x0F, false }, { 13, 0x1C, false }, { 16, 0x2A, false },
		{ 17, 0x1D, false }, { 18, 0x38, false }, { 19, 0x45, false }, { 20, 0x3A, false },
		{ 27, 0x01, false }, { 32, 0x39, false }, { 33, 0x49, true }, { 34, 0x51, true },
		{ 35, 0x4F, true }, { 36, 0x47, true }, { 37, 0x4B, true }, { 38, 0x48, true },
		{ 39, 0x4D, true }, { 40, 0x50, true }, { 45, 0x52, true }, { 46, 0x53, true },
		{ 48, 0x0B, false }, { 49, 0x02, false }, { 50, 0x03, false }, { 51, 0x04, false },
		{ 52, 0x05, false }, { 53, 0x06, false }, { 54, 0x07, false }, { 55, 0x08, false },
		{ 56, 0x09, false }, { 57, 0x0A, false }, { 65, 0x1E, false }, { 66, 0x30, false },
		{ 67, 0x2E, false }, { 68, 0x20, false }, { 69, 0x12, false }, { 70, 0x21, false },
		{ 71, 0x22, false }, { 72, 0x23, false }, { 73, 0x17, false }, { 74, 0x24, false },
		{ 75, 0x25, false }, { 76, 0x26, false }, { 77, 0x32, false }, { 78, 0x31, false },
		{ 79, 0x18, false }, { 80, 0x19, false }, { 81, 0x10, false }, { 82, 0x13, false },
		{ 83, 0x1F, false }, { 84, 0x14, false }, { 85, 0x16, false }, { 86, 0x2F, false },
		{ 87, 0x11, false }, { 88, 0x2D, false }, { 89, 0x15, false }, { 90, 0x2C, false },
		{ 112, 0x3B, false }, { 113, 0x3C, false }, { 114, 0x3D, false }, { 115, 0x3E, false },
		{ 116, 0x3F, false }, { 117, 0x40, false }, { 118, 0x41, false }, { 119, 0x42, false },
		{ 120, 0x43, false }, { 121, 0x44, false }, { 122, 0x57, false }, { 123, 0x58, false },
		{ 186, 0x27, false }, { 187, 0x0D, false }, { 188, 0x33, false }, { 189, 0x0C, false },
		{ 190, 0x34, false }, { 191, 0x35, false }, { 192, 0x29, false }, { 219, 0x1A, false },
		{ 220, 0x2B, false }, { 221, 0x1B, false }, { 222, 0x28, false }
	};

	for (const KeyMapping& key : keymap) {
		if (key.key_code != key_code) {
			continue;
		}
		uint8_t scancode = pressed ? key.scancode : static_cast<uint8_t>(key.scancode | 0x80);
		if (key.extended) {
			ud.kbd->send_scancodes({ 0xE0, scancode });
		}
		else {
			ud.kbd->send_scancode(scancode);
		}
		return;
	}
}
