#pragma once
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/opt.h>
#include <libavutil/time.h>
#include <libswscale/swscale.h>
}
#include <string>
class Streamer {
private:
	int width=1600;
	int height=900;
	int fps = 30;
	int64_t pts = 0;
	SwsContext* sws_ctx = nullptr;
	uint8_t* src_ptr=nullptr;
	AVFrame* frame=nullptr;
	AVFormatContext* fmt_ctx=nullptr;
	AVCodecContext* codec_ctx=nullptr;
	AVStream* stream=nullptr;
	AVPacket* pkt=nullptr;
public:
	volatile bool isActive = false;
	void UpdateFrame(uint8_t* src_ptr);
	void SetSwsCtx();
	void Create(std::string uri);
	void Destroy();
};