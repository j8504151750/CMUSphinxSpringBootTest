package com.lucas.cmusphinxtest;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.SpeechResult;
import edu.cmu.sphinx.api.StreamSpeechRecognizer;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class VoiceController {

    @PostMapping("/upload")
    public ResponseEntity<String> upload(MultipartFile file) {
        Path tempDir = null;

        try {
            // 動態創建臨時目錄
            tempDir = Files.createTempDirectory("uploads");
            File uploadDir = tempDir.toFile();

            // 保存錄音文件到臨時目錄
            File tempFile = new File(uploadDir, "audio.webm");
            file.transferTo(tempFile);

            // 將音頻文件轉換為 CMUSphinx 支持的格式 (WAV, 16kHz, 單聲道)
            File convertedFile = new File(uploadDir, "converted_audio.wav");
            convertAudio(tempFile, convertedFile);

            // 配置 CMUSphinx
            Configuration configuration = new Configuration();
            configuration.setAcousticModelPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.cd_cont_5000");
            configuration.setDictionaryPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.dic");
            configuration.setLanguageModelPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.lm.bin");

            // 加載音頻進行語音識別
            StreamSpeechRecognizer recognizer = new StreamSpeechRecognizer(configuration);
            InputStream stream = new FileInputStream(convertedFile);
            recognizer.startRecognition(stream);
            SpeechResult result;

            StringBuilder resultText = new StringBuilder();
            while ((result = recognizer.getResult()) != null) {
                System.out.println("識別結果: " + result.getHypothesis());
                resultText.append(result.getHypothesis()).append("\n");
            }
            recognizer.stopRecognition();

            // 刪除臨時文件
            tempFile.delete();
            convertedFile.delete();

            // 返回識別結果
            return ResponseEntity.ok(resultText.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("處理語音失敗！");
        } finally {
            // 最後刪除臨時目錄
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((p1, p2) -> p2.compareTo(p1)) // 從底部往上排序
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void convertAudio(File inputFile, File outputFile) throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, 1);

        grabber.start();

        recorder.setAudioChannels(1);
        recorder.setSampleRate(16000);
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
        recorder.start();

        while (true) {
            org.bytedeco.javacv.Frame frame = grabber.grab();
            if (frame == null) break;
            recorder.record(frame);
        }

        recorder.stop();
        grabber.stop();
    }
}
