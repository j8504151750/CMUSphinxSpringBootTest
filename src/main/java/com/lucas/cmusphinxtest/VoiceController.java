package com.lucas.cmusphinxtest;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.SpeechResult;
import edu.cmu.sphinx.api.StreamSpeechRecognizer;
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
            File tempFile = new File(uploadDir, "audio.wav");
            file.transferTo(tempFile);

            // 配置 CMUSphinx
            Configuration configuration = new Configuration();
            configuration.setAcousticModelPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.cd_cont_5000");
            configuration.setDictionaryPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.dic");
            configuration.setLanguageModelPath("resource:/cmusphinx-zh-cn-5.2/zh_cn.lm.bin");

            // 加載音頻進行語音識別
            StreamSpeechRecognizer recognizer = new StreamSpeechRecognizer(configuration);
            InputStream stream = new FileInputStream(tempFile);
            recognizer.startRecognition(stream);
            SpeechResult result;

            StringBuilder resultText = new StringBuilder();
            while ((result = recognizer.getResult()) != null) {
                resultText.append(result.getHypothesis()).append("\n");
            }
            recognizer.stopRecognition();

            // 刪除臨時文件
            tempFile.delete();

            // 返回識別結果
            return ResponseEntity.ok(resultText.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("處理語音失敗！");
        } finally {
            // 最後刪除臨時目錄
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
