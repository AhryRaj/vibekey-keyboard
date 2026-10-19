package com.cinepulse.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * AudioGifCompiler
 * Uses 100% official Android SDK APIs (Bitmap.compress, MediaCodec, MediaMuxer, MediaExtractor)
 * to prepare 512x512 .webp Stickers, .gif animations, and 480x480 H.264+AAC .mp4 Audio-GIFs.
 */
public class AudioGifCompiler {

    public static File buildStickerWebpFile(Context context, OfficialMediaRepository.MediaItem item) throws Exception {
        File outFile = new File(context.getFilesDir(), "kb_sticker_" + item.id + ".webp");
        Bitmap src = OfficialMediaRepository.loadItemPreviewBitmap(context, item);

        Bitmap canvasBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBmp);
        canvas.drawColor(Color.TRANSPARENT);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (src != null) {
            RectF dst = new RectF(16f, 16f, 496f, 496f);
            canvas.drawBitmap(src, null, dst, p);
        } else {
            p.setColor(Color.parseColor("#4F46E5"));
            canvas.drawRoundRect(new RectF(24f, 24f, 488f, 488f), 64f, 64f, p);
        }

        if (item.caption != null && !item.caption.trim().isEmpty()) {
            drawStickerCaption(canvas, 512, item.caption.trim().toUpperCase());
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            canvasBmp.compress(Bitmap.CompressFormat.WEBP, 82, fos);
            fos.flush();
        }
        return outFile;
    }

    public static File buildStickerPngFile(Context context, OfficialMediaRepository.MediaItem item) throws Exception {
        File outFile = new File(context.getFilesDir(), "kb_sticker_" + item.id + ".png");
        Bitmap src = OfficialMediaRepository.loadItemPreviewBitmap(context, item);

        Bitmap canvasBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBmp);
        canvas.drawColor(Color.TRANSPARENT);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (src != null) {
            RectF dst = new RectF(16f, 16f, 496f, 496f);
            canvas.drawBitmap(src, null, dst, p);
        } else {
            p.setColor(Color.parseColor("#4F46E5"));
            canvas.drawRoundRect(new RectF(24f, 24f, 488f, 488f), 64f, 64f, p);
        }

        if (item.caption != null && !item.caption.trim().isEmpty()) {
            drawStickerCaption(canvas, 512, item.caption.trim().toUpperCase());
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            canvasBmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        }
        return outFile;
    }

    public static File buildGifOrPngFile(Context context, OfficialMediaRepository.MediaItem item) throws Exception {
        byte[] raw = OfficialMediaRepository.loadItemRawBytes(context, item);
        boolean isGif = raw != null && raw.length > 4 && raw[0] == 'G' && raw[1] == 'I' && raw[2] == 'F';
        String ext = isGif ? ".gif" : ".png";
        File outFile = new File(context.getFilesDir(), "kb_visual_" + item.id + ext);
        if (raw != null && raw.length > 0) {
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(raw);
                fos.flush();
            }
            return outFile;
        }
        return buildStickerWebpFile(context, item);
    }

    @SuppressWarnings("deprecation")
    public static File buildAudioGifMp4File(Context context, OfficialMediaRepository.MediaItem item) throws Exception {
        File outMp4 = new File(context.getFilesDir(), "kb_audiogif_" + item.id + ".mp4");
        if (outMp4.exists()) {
            outMp4.delete();
        }

        byte[] visualBytes = OfficialMediaRepository.loadItemRawBytes(context, item);
        Movie gifMovie = null;
        Bitmap staticBmp = null;
        if (visualBytes != null && visualBytes.length > 0) {
            try {
                gifMovie = Movie.decodeByteArray(visualBytes, 0, visualBytes.length);
                if (gifMovie != null && (gifMovie.width() <= 0 || gifMovie.height() <= 0)) {
                    gifMovie = null;
                }
            } catch (Exception ignored) {
                gifMovie = null;
            }
            if (gifMovie == null) {
                staticBmp = BitmapFactory.decodeByteArray(visualBytes, 0, visualBytes.length);
            }
        }

        File mp3File = new File(context.getCacheDir(), "kb_temp_audio.mp3");
        try (InputStream is = context.getAssets().open(item.assetAudioPath);
             FileOutputStream fos = new FileOutputStream(mp3File)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }

        final int width = 480;
        final int height = 480;
        final int fps = 15;
        final int totalFrames = fps * 3; // 3.0s crisp Audio-GIF

        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 900_000);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoEncoder.start();

        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        int sampleRate = 44100;
        int channelCount = 1;

        if (mp3File.exists() && mp3File.length() > 100) {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(mp3File.getAbsolutePath());
                if (extractor.getTrackCount() > 0) {
                    MediaFormat inAudioFormat = extractor.getTrackFormat(0);
                    String mime = inAudioFormat.getString(MediaFormat.KEY_MIME);
                    if (inAudioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = inAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (inAudioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = inAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    extractor.selectTrack(0);
                    MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(inAudioFormat, null, null, 0);
                    decoder.start();

                    MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
                    boolean inDone = false;
                    boolean outDone = false;
                    int maxBytes = sampleRate * channelCount * 2 * 3;

                    while (!outDone && pcmOut.size() < maxBytes) {
                        if (!inDone) {
                            int inIdx = decoder.dequeueInputBuffer(5000);
                            if (inIdx >= 0) {
                                ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                                int read = extractor.readSampleData(inBuf, 0);
                                if (read < 0) {
                                    decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inDone = true;
                                } else {
                                    long pts = extractor.getSampleTime();
                                    decoder.queueInputBuffer(inIdx, 0, read, pts, 0);
                                    extractor.advance();
                                }
                            }
                        }
                        int outIdx = decoder.dequeueOutputBuffer(decInfo, 5000);
                        if (outIdx >= 0) {
                            ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                            if (decInfo.size > 0 && outBuf != null) {
                                outBuf.position(decInfo.offset);
                                outBuf.limit(decInfo.offset + decInfo.size);
                                byte[] chunk = new byte[decInfo.size];
                                outBuf.get(chunk);
                                pcmOut.write(chunk);
                            }
                            decoder.releaseOutputBuffer(outIdx, false);
                            if ((decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outDone = true;
                            }
                        }
                    }
                    decoder.stop();
                    decoder.release();
                }
            } catch (Exception ignored) {
            } finally {
                extractor.release();
            }
        }

        byte[] pcmBytes = pcmOut.toByteArray();
        if (pcmBytes.length < 4096) {
            sampleRate = 44100;
            channelCount = 1;
            int samples = sampleRate * 3;
            pcmBytes = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                double t = (double) i / sampleRate;
                double freq = (i % (sampleRate / 4) < (sampleRate / 8)) ? 320.0 : 440.0;
                short val = (short) (Math.sin(2.0 * Math.PI * freq * t) * 9000);
                pcmBytes[i * 2] = (byte) (val & 0xff);
                pcmBytes[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
            }
        }

        MediaFormat aacFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
        aacFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        aacFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96_000);
        aacFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        MediaCodec audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();

        MediaMuxer muxer = new MediaMuxer(outMp4.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int videoTrackIndex = -1;
        int audioTrackIndex = -1;
        boolean muxerStarted = false;

        Bitmap frameBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas frameCanvas = new Canvas(frameBmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        MediaCodec.BufferInfo vInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo aInfo = new MediaCodec.BufferInfo();

        int frameIndex = 0;
        boolean videoInDone = false;
        boolean videoOutDone = false;
        int pcmOffset = 0;
        boolean audioInDone = false;
        boolean audioOutDone = false;

        while (!videoOutDone || !audioOutDone) {
            if (!videoInDone) {
                int inIdx = videoEncoder.dequeueInputBuffer(5000);
                if (inIdx >= 0) {
                    if (frameIndex >= totalFrames) {
                        videoEncoder.queueInputBuffer(inIdx, 0, 0, (frameIndex * 1_000_000L) / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        videoInDone = true;
                    } else {
                        frameCanvas.drawColor(Color.parseColor("#0F172A"));
                        if (gifMovie != null) {
                            int dur = gifMovie.duration();
                            if (dur <= 0) dur = 1000;
                            int relTime = (int) (((frameIndex * 1000L) / fps) % dur);
                            gifMovie.setTime(relTime);
                            float sx = (float) width / gifMovie.width();
                            float sy = (float) height / gifMovie.height();
                            float s = Math.min(sx, sy);
                            float dx = (width - gifMovie.width() * s) / 2f;
                            float dy = (height - gifMovie.height() * s) / 2f;
                            frameCanvas.save();
                            frameCanvas.translate(dx, dy);
                            frameCanvas.scale(s, s);
                            gifMovie.draw(frameCanvas, 0, 0);
                            frameCanvas.restore();
                        } else if (staticBmp != null) {
                            RectF dst = new RectF(0, 0, width, height);
                            frameCanvas.drawBitmap(staticBmp, null, dst, paint);
                        }

                        if (item.caption != null && !item.caption.trim().isEmpty()) {
                            drawStickerCaption(frameCanvas, width, item.caption.trim().toUpperCase());
                        }

                        byte[] yuv = bitmapToNV12(frameBmp, width, height);
                        ByteBuffer inBuf = videoEncoder.getInputBuffer(inIdx);
                        if (inBuf != null) {
                            inBuf.clear();
                            int len = Math.min(inBuf.remaining(), yuv.length);
                            inBuf.put(yuv, 0, len);
                            long ptsUs = (frameIndex * 1_000_000L) / fps;
                            videoEncoder.queueInputBuffer(inIdx, 0, len, ptsUs, 0);
                        }
                        frameIndex++;
                    }
                }
            }

            if (!audioInDone) {
                int inIdx = audioEncoder.dequeueInputBuffer(5000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = audioEncoder.getInputBuffer(inIdx);
                    if (pcmOffset >= pcmBytes.length || inBuf == null) {
                        long ptsUs = (pcmOffset / (2L * channelCount)) * 1_000_000L / sampleRate;
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        audioInDone = true;
                    } else {
                        inBuf.clear();
                        int chunk = Math.min(Math.min(inBuf.remaining(), 4096), pcmBytes.length - pcmOffset);
                        inBuf.put(pcmBytes, pcmOffset, chunk);
                        long ptsUs = (pcmOffset / (2L * channelCount)) * 1_000_000L / sampleRate;
                        audioEncoder.queueInputBuffer(inIdx, 0, chunk, ptsUs, 0);
                        pcmOffset += chunk;
                    }
                }
            }

            if (!videoOutDone) {
                int outIdx = videoEncoder.dequeueOutputBuffer(vInfo, 5000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                    if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !muxerStarted) {
                        muxer.start();
                        muxerStarted = true;
                    }
                } else if (outIdx >= 0) {
                    ByteBuffer outBuf = videoEncoder.getOutputBuffer(outIdx);
                    if ((vInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        vInfo.size = 0;
                    }
                    if (vInfo.size > 0 && muxerStarted && outBuf != null) {
                        outBuf.position(vInfo.offset);
                        outBuf.limit(vInfo.offset + vInfo.size);
                        muxer.writeSampleData(videoTrackIndex, outBuf, vInfo);
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false);
                    if ((vInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoOutDone = true;
                    }
                }
            }

            if (!audioOutDone) {
                int outIdx = audioEncoder.dequeueOutputBuffer(aInfo, 5000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                    if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !muxerStarted) {
                        muxer.start();
                        muxerStarted = true;
                    }
                } else if (outIdx >= 0) {
                    ByteBuffer outBuf = audioEncoder.getOutputBuffer(outIdx);
                    if ((aInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        aInfo.size = 0;
                    }
                    if (aInfo.size > 0 && muxerStarted && outBuf != null) {
                        outBuf.position(aInfo.offset);
                        outBuf.limit(aInfo.offset + aInfo.size);
                        muxer.writeSampleData(audioTrackIndex, outBuf, aInfo);
                    }
                    audioEncoder.releaseOutputBuffer(outIdx, false);
                    if ((aInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        audioOutDone = true;
                    }
                }
            }
        }

        videoEncoder.stop();
        videoEncoder.release();
        audioEncoder.stop();
        audioEncoder.release();
        if (muxerStarted) {
            muxer.stop();
        }
        muxer.release();
        return outMp4;
    }

    private static void drawStickerCaption(Canvas canvas, int size, String text) {
        if (text.length() > 22) text = text.substring(0, 22);
        Paint banner = new Paint(Paint.ANTI_ALIAS_FLAG);
        banner.setColor(Color.argb(215, 15, 23, 42));
        float top = size - 78f;
        float bottom = size - 20f;
        canvas.drawRoundRect(new RectF(24f, top, size - 24f, bottom), 16f, 16f, banner);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, size / 2f, top + 39f, textPaint);
    }

    private static byte[] bitmapToNV12(Bitmap bmp, int width, int height) {
        int[] argb = new int[width * height];
        bmp.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] yuv = new byte[width * height * 3 / 2];
        int yIndex = 0;
        int uvIndex = width * height;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int c = argb[j * width + i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));
                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv.length) {
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
                    yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, v));
                }
            }
        }
        return yuv;
    }
}
