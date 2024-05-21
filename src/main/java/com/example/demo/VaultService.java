package com.example.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenRequest.VaultTokenRequestBuilder;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(VaultProps.class)
@RequiredArgsConstructor
@Service
public class VaultService {
  private final VaultOperations vaultOperations;
  private final VaultProps vaultProperties;

  @Value("${vault.transit.keyName}")
  private String transitKeyName;

  @Value("${vault.token.entityAlias}")
  private String tokenEntityAlias;

  @Value("${vault.token.role}")
  private String tokenRole;

  @Value("${vault.token.consumerPolicyName}")
  private String tokenConsumerPolicyName;

  public VaultEndpoint vaultEndpoint() {
    VaultEndpoint vaultEndpoint = new VaultEndpoint();
    if (vaultProperties != null) {
      vaultEndpoint.setHost(vaultProperties.getHost());
      vaultEndpoint.setPort(vaultProperties.getPort());
      vaultEndpoint.setScheme(vaultProperties.getScheme());
    }
    return vaultEndpoint;
  }

  @SuppressWarnings("null")
  public Map<String, String> vaultInfo() {
    Map<String, String> map = new HashMap<>(); // 맵 선언 및 초기화
    if (vaultProperties != null) {
      map.put("scheme", vaultProperties.getScheme());
      map.put("host", vaultProperties.getHost());
      map.put("port", String.valueOf(vaultProperties.getPort())); // 포트를 문자열로 변환하여 추가
    }
    map.put("version", this.vaultOperations.opsForSys().health().getVersion());
    return map;
  }

  @SuppressWarnings("null")
  public String getPublicKey(String id) {
    // Get Consumer Token
    VaultTokenRequestBuilder builder = VaultTokenRequest.builder()
        .displayName(id)
        .entityAlias(tokenEntityAlias)
        .noDefaultPolicy()
        .noParent();

    builder.withPolicy(tokenConsumerPolicyName);

    VaultTokenResponse vaultTokenResponse = vaultOperations.opsForToken().create(tokenRole, builder.build());
    VaultToken consumerToken = vaultTokenResponse.getToken();

    VaultTemplate vaultTemplate = new VaultTemplate(vaultEndpoint(), new TokenAuthentication(consumerToken));
    VaultTransitKey consumerPublicKey = vaultTemplate.opsForTransit().getKey(transitKeyName);

    String lastVersion = String.valueOf(consumerPublicKey.getLatestVersion());
    Map<String, Object> keyData = (Map<String, Object>) consumerPublicKey.getKeys().get(lastVersion);

    String publicKey = "";
    if (keyData != null) {
      publicKey = (String) keyData.get("public_key");
    }

    return publicKey;
  }

  @SuppressWarnings("null")
  public String getPrivateKey(String code) {
    VaultResponse vaultResponseRoleId = vaultOperations
        .read(String.format("auth/approle/role/insurer-role-%s/role-id", code));

    String appRoleId = vaultResponseRoleId.getData().get("role_id").toString();

    VaultResponse vaultResponseSecretId = vaultOperations
        .write(String.format("auth/approle/role/insurer-role-%s/secret-id", code));
    String appSecretId = vaultResponseSecretId.getData().get("secret_id").toString();

    AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
        .roleId(RoleId.provided(appRoleId))
        .secretId(AppRoleAuthenticationOptions.SecretId.provided(appSecretId))
        .build();

    RestOperations restOperations = RestTemplateBuilder.builder().endpoint(vaultEndpoint()).build();
    AppRoleAuthentication appRoleAuthentication = new AppRoleAuthentication(options, restOperations);

    VaultTemplate vaultTemplate = new VaultTemplate(vaultEndpoint(), appRoleAuthentication);

    VaultTransitKey consumerPublicKey = vaultTemplate.opsForTransit("transit").getKey(transitKeyName);
    String lastVersion = consumerPublicKey.getLatestVersion() + "";

    RawTransitKey insurerPrivateKey = vaultTemplate.opsForTransit("transit").exportKey(transitKeyName,
        TransitKeyType.ENCRYPTION_KEY);
    String lastPrivateKey = insurerPrivateKey.getKeys().get(lastVersion).toString();

    return lastPrivateKey;
  }
}
