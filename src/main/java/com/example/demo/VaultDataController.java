package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.lang.String;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class VaultDataController {

  @Value("${page.insurers.codes}")
  private List<String> insurersCodes;

  @Autowired
  private VaultService vaultService;

  @GetMapping("/")
  public String getVaultHealthCheck(Model model) {
    Map<String, String> vaultInfo = vaultService.vaultInfo();
    String vaultUrl = vaultInfo.get("scheme") + "://" + vaultInfo.get("host") + ":" + vaultInfo.get("port");
    model.addAttribute("vault_url", vaultUrl);
    model.addAttribute("vault_version", vaultInfo.get("version"));
    model.addAttribute("insurers_codes", insurersCodes);
    return "index.html";
  }

  @GetMapping("/consumer")
  public ResponseEntity<InputStreamResource> downloadPublicKey(@RequestParam String id) {
    try {
      String encodedFileName = URLEncoder.encode(id + "_rsa.pem.pub", "UTF-8");

      String lastPublicKey = vaultService.getPublicKey(id);

      // InputStreamResource로 변환
      InputStream inputStream = new ByteArrayInputStream(lastPublicKey.getBytes());
      InputStreamResource resource = new InputStreamResource(inputStream);

      // 응답을 구성하고 반환
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .body(resource);

    } catch (IOException e) {
      e.printStackTrace();
    }

    return ResponseEntity.notFound().build(); // 파일이 없거나 처리에 실패한 경우 404 응답 반환
  }

  @GetMapping("/insurer")
  public ResponseEntity<InputStreamResource> downloadPrivateKey(@RequestParam String code) {
    try {
      String encodedFileName = URLEncoder.encode(code + "_rsa.pem", "UTF-8");

      String lastPrivateKey = vaultService.getPrivateKey(code);

      // InputStreamResource로 변환
      InputStream inputStream = new ByteArrayInputStream(lastPrivateKey.getBytes());
      InputStreamResource resource = new InputStreamResource(inputStream);

      // 응답을 구성하고 반환
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .body(resource);

    } catch (IOException e) {
      e.printStackTrace();
    }

    return ResponseEntity.notFound().build(); // 파일이 없거나 처리에 실패한 경우 404 응답 반환
  }
}