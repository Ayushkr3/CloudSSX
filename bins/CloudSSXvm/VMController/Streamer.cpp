#include "Streamer.h"
#include <stdexcept>
#define CHECK(res) if(res<0) isActive = false
void Streamer::UpdateFrame(uint8_t* src_ptr)
{
	const uint8_t* srcSlice[1] = { src_ptr };
	int            srcStride[1] = { width*4 };
	int res = sws_scale(sws_ctx,srcSlice, srcStride,0, height,frame->data, frame->linesize);
	frame->pts = pts++;
	CHECK(avcodec_send_frame(codec_ctx, frame));
	int ret = 0;
	while (ret >= 0) {
		ret = avcodec_receive_packet(codec_ctx, pkt);
		if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
		else {
			if (ret < 0) {
				char err[AV_ERROR_MAX_STRING_SIZE];
				av_strerror(ret, err, sizeof(err));
				//std::cerr << "Error: " << err << std::endl;
				isActive = false;
			}
			/*isActive = false;
			break;*/
		}

		av_packet_rescale_ts(pkt,
			codec_ctx->time_base,
			stream->time_base);
		pkt->stream_index = stream->index;

		CHECK(av_interleaved_write_frame(fmt_ctx, pkt));
		av_packet_unref(pkt);
	}
}
void Streamer::SetSwsCtx() {

	sws_ctx = sws_getContext(width, height, AV_PIX_FMT_BGR0, width, height, AV_PIX_FMT_YUV420P,0, nullptr, nullptr, nullptr);
	if (!sws_ctx) {
		throw std::runtime_error("Failed to create SwsContext");
	}
}

void Streamer::Create(std::string uri)
{
	isActive = true;
	CHECK(avformat_alloc_output_context2(&fmt_ctx,nullptr,"rtsp", uri.data()));
	stream = avformat_new_stream(fmt_ctx, nullptr);
	codec_ctx = avcodec_alloc_context3(avcodec_find_encoder_by_name("h264_amf"));
	codec_ctx->width = width;
	codec_ctx->height = height;
	codec_ctx->pix_fmt = AV_PIX_FMT_YUV420P;
	codec_ctx->time_base = { 1, fps };
	codec_ctx->framerate = { fps, 1 };
	codec_ctx->bit_rate = 4'000'000;
	codec_ctx->gop_size = fps;
	codec_ctx->max_b_frames = 0;
	AVDictionary* codecOpts = nullptr;

	av_dict_set(&codecOpts, "usage", "ultralowlatency", 0);
	av_dict_set(&codecOpts, "quality", "speed", 0);

	CHECK(avcodec_open2(codec_ctx, avcodec_find_encoder_by_name("h264_amf"), &codecOpts));
	av_dict_free(&codecOpts);
	codec_ctx->max_b_frames = 0;
	CHECK(avcodec_parameters_from_context(stream->codecpar, codec_ctx));
	stream->time_base = codec_ctx->time_base;

	AVDictionary* opts = nullptr;
	av_dict_set(&opts, "rtsp_transport", "udp", 0);

	//CHECK(avformat_write_header(fmt_ctx, &opts));
	int ret = avformat_write_header(fmt_ctx, &opts);
	av_dict_free(&opts);
	frame = av_frame_alloc();
	SetSwsCtx();
	frame->format = AV_PIX_FMT_YUV420P;
	frame->width = width;
	frame->height = height;
	CHECK(av_frame_get_buffer(frame, 32));
	pkt = av_packet_alloc();
}

void Streamer::Destroy()
{
	if (fmt_ctx) {
		avcodec_send_frame(codec_ctx, nullptr);
		int ret = 0;
		while (ret >= 0) {
			ret = avcodec_receive_packet(codec_ctx, pkt);
			if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
			if (ret < 0) break;
			av_packet_rescale_ts(pkt, codec_ctx->time_base, stream->time_base);
			pkt->stream_index = stream->index;
			av_interleaved_write_frame(fmt_ctx, pkt);
			av_packet_unref(pkt);
		}
		av_write_trailer(fmt_ctx);
		avio_closep(&fmt_ctx->pb);
		avformat_free_context(fmt_ctx);
	}
	if (frame) {
		av_frame_free(&frame);
	}
	if (codec_ctx) {
		avcodec_free_context(&codec_ctx);
	}
	if (pkt) {
		av_packet_free(&pkt);
	}
}
