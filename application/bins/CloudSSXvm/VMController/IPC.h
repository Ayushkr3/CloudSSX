#pragma once
#include <atomic>
#include <string>
#include <Windows.h>
#include <functional>
class IPC {
public:
	struct Data_Packet {
		int mouseX=0;
		int mouseY=0;
		int key_code=0;
		int is_mouse_keyboard=-1;
	};
private:
	struct SYNC_STRUCT {
	std::atomic<uint64_t> write;
	std::atomic<uint64_t> read;
	std::atomic<uint64_t> notify;
	};
	HANDLE synchMap=nullptr;
	HANDLE data_packet_handle=nullptr;
	Data_Packet* data_packet_buffer = nullptr;
	std::function<void(Data_Packet)> callback_func = nullptr;
	
public:
	SYNC_STRUCT* sync = nullptr;
	~IPC();
	bool Create(const std::string& uri);
	void Poll();
	void RegisterCallBack(std::function<void(Data_Packet)> callback);
};
