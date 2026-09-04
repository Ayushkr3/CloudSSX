#include <iostream>
#include <thread>
#include <chrono>
#include<vector>
#include "Streamer.h"
#include "VM/VM.h"
#include "VM/memory.h"
#include "VM/Global.h"
#include <string>
#include "IPC.h"
#include <chrono>
void parse_incoming_packet(IPC::Data_Packet packet) {

	switch (packet.is_mouse_keyboard) {
	case 0:
		SendMouseInput(packet.mouseX, packet.mouseY, -1, false);
		break;
	case 1:
		SendMouseInput(0, 0, packet.mouseX, packet.mouseY != 0);
		break;
	case 2:
		SendKeyboardInput(packet.key_code, packet.mouseX != 0);
		break;
	}
	//do something cool here
}
void SendVideoStream(Streamer* strm)
{
	using clock = std::chrono::steady_clock;

	constexpr auto frameTime = std::chrono::milliseconds(33);
	auto next = clock::now();

	while (running.load())
	{
		strm->UpdateFrame((uint8_t*)SVGA);

		next += frameTime;
		std::this_thread::sleep_until(next);
	}
}
int main()
{
	std::thread MainVM(StartVM);
	Sleep(2000); 	//Spooky race condition
	//av_log_set_level(AV_LOG_TRACE);
	IPC ipc;
	ipc.RegisterCallBack(parse_incoming_packet);
	std::string uri;
	std::cin >> uri;
	bool res = ipc.Create(uri);
	if (res) {
		std::cout << "Fine" << std::flush;
	}
	else {
		std::cout << "NFine" << std::flush;
		return 0;
	}
	Streamer strm;
	strm.Create("rtsp://127.0.0.1:8554/"+uri);
	std::thread streaming(SendVideoStream,&strm);
	char dummy;
	while (true)
	{
		std::cin.get(dummy);
		if (dummy == 'e') {
			break;	
		}
		ipc.sync->notify.store(0, std::memory_order_release);
		ipc.Poll();
	}

	running.store(false);

	//MainVM.join();
	streaming.join();
	strm.Destroy();
}
