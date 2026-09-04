#include "IPC.h"
constexpr int BUFFER_LEN = 1024;
bool IPC::Create(const std::string& uri) {
    synchMap = OpenFileMappingA(FILE_MAP_ALL_ACCESS,FALSE,("Local\\"+uri).c_str());
    if (!synchMap) {
        return false;
    }
    data_packet_handle = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, ("Local\\kbm" + uri).c_str());
    if (!data_packet_handle) {
        CloseHandle(synchMap);
        synchMap = nullptr;
        return false;
    }
    sync = reinterpret_cast<SYNC_STRUCT*>(MapViewOfFile(synchMap,FILE_MAP_ALL_ACCESS,0,0,sizeof(SYNC_STRUCT)));
    if (!sync) {
        CloseHandle(data_packet_handle);
        CloseHandle(synchMap);
        data_packet_handle = nullptr;
        synchMap = nullptr;
        return false;
    }
    sync->write.store(0, std::memory_order_release);
    sync->read.store(0, std::memory_order_release);
    sync->notify.store(0, std::memory_order_release);
    data_packet_buffer = reinterpret_cast<Data_Packet*>(MapViewOfFile(data_packet_handle, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(Data_Packet)*BUFFER_LEN));
    if (!data_packet_buffer) {
        UnmapViewOfFile(sync);
        CloseHandle(data_packet_handle);
        CloseHandle(synchMap);
        sync = nullptr;
        data_packet_handle = nullptr;
        synchMap = nullptr;
        return false;
    }
    ZeroMemory(data_packet_buffer, BUFFER_LEN * sizeof(Data_Packet));
    return true;
}
IPC::~IPC() {
    if (data_packet_buffer) {
        UnmapViewOfFile(data_packet_buffer);
    }
    if (sync) {
        UnmapViewOfFile(sync);
    }
    if (data_packet_handle) {
        CloseHandle(data_packet_handle);
    }
    if (synchMap) {
        CloseHandle(synchMap);
    }
}
void IPC::Poll() {
    if (sync&&data_packet_buffer) {
        uint64_t read = sync->read.load(std::memory_order_acquire);
        uint64_t write = sync->write.load(std::memory_order_acquire);
        while (read != write) {
            //Call callback with packet
            Data_Packet packet = data_packet_buffer[read %BUFFER_LEN];
            if (this->callback_func != nullptr) {
                callback_func(packet);
            }
            read++;
            sync->read.store(read, std::memory_order_release);
            write = sync->write.load(std::memory_order_acquire);
        }
    }
}
void IPC::RegisterCallBack(std::function<void(Data_Packet)> callback) {
    this->callback_func = callback;
}
