server.port=${server_port}

spring.application.name=demo

spring.cloud.vault.host=${vault_host}
spring.cloud.vault.port=${vault_port}
spring.cloud.vault.scheme=${vault_scheme}
# spring.cloud.vault.authentication=TOKEN
# spring.cloud.vault.token=root
spring.cloud.vault.authentication=APPROLE
spring.cloud.vault.app-role.role-id=${app_id}
spring.cloud.vault.app-role.secret-id=${secret_id}
spring.cloud.vault.app-role.role=${approle_name}
spring.cloud.vault.app-role.app-role-path=${approle_path}

vault.transit.keyName=${key_name}
vault.token.entityAlias=${entity_alias_name}
vault.token.role=${role_name}
vault.token.consumerPolicyName=${consumer_policy_name}

spring.thymeleaf.check-template-location=true
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.content-type=text/html
spring.thymeleaf.cache=false

page.insurers.codes=${insueres_codes}