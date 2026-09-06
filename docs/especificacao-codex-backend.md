# Especificação para Codex — Backend
## Async Photo Processing Platform

> **Fase 1 do projeto.**
>
> Esta documentação é exclusiva para implementação do backend.
> **Não implementar frontend nesta fase.**
> O backend deverá ser desenvolvido, executado e validado primeiro por meio de testes automatizados e testes funcionais utilizando Postman.

---

# 1. Objetivo

Implementar o backend de um sistema assíncrono de cadastro de usuários e processamento de fotos de perfil.

O backend deverá permitir:

- cadastrar usuário com nome e foto obrigatórios;
- gerar `usuarioId` automaticamente;
- listar usuários;
- consultar usuário;
- atualizar nome;
- excluir usuário;
- substituir foto;
- consultar foto atual;
- consultar status de processamento;
- processar imagens de forma assíncrona;
- armazenar imagens original e processada separadamente;
- publicar e consumir eventos via Pub/Sub;
- persistir dados e imagem processada no MySQL;
- manter rastreabilidade por `processamentoId`.

O foco desta fase é backend, arquitetura orientada a eventos, mensageria, serverless, armazenamento de objetos, banco de dados e testes.

---

# 2. Regra de execução das fases

O projeto será implementado em duas fases independentes:

```text
FASE 1
Backend
↓
Testes automatizados
↓
Validação funcional com Postman
↓
Backend considerado concluído

FASE 2
Frontend
```

Durante a Fase 1:

- não criar aplicação frontend;
- não criar páginas HTML da aplicação;
- não criar Vite;
- não criar TypeScript de interface;
- não antecipar componentes da Fase 2.

A Fase 2 somente deverá começar após os critérios de sucesso desta documentação terem sido validados.

---

# 3. Arquitetura

A arquitetura terá:

```text
2 microserviços Spring Boot
+
1 função serverless
```

Componentes:

```text
Postman
   ↓
photo-api
Java + Spring Boot
   │
   ├── MySQL
   │
   └── Storage original
           ↓
      photo-processor
      Cloud Run Function
           ↓
      Storage processado
           ↓
         Pub/Sub
           ↓
      photo-consumer
      Java + Spring Boot
           ↓
         MySQL
```

## 3.1 photo-api

Responsabilidades:

- cadastrar usuário;
- validar nome e foto inicial;
- gerar `usuarioId`;
- criar `processamentoId`;
- listar usuários;
- consultar usuário;
- atualizar nome;
- excluir usuário;
- receber atualização posterior de foto;
- validar formato e tamanho da imagem;
- armazenar imagem original;
- registrar estados `RECEBIDA` e `PROCESSANDO`;
- consultar processamento;
- disponibilizar foto atual.

## 3.2 photo-processor

Função serverless.

Responsabilidades:

- receber o evento/gatilho de uma imagem original;
- recuperar a imagem;
- otimizar a imagem;
- armazenar a imagem processada;
- publicar diretamente o resultado no Pub/Sub.

Não deverá:

- ser implementado como aplicação Spring Boot completa;
- acessar diretamente o MySQL.

## 3.3 photo-consumer

Segundo microserviço Spring Boot.

Responsabilidades:

- consumir mensagens Pub/Sub;
- validar mensagem;
- garantir idempotência;
- registrar `PROCESSADA`;
- registrar `PERSISTINDO`;
- recuperar imagem processada;
- persistir imagem e metadados no MySQL;
- registrar `PERSISTIDA`;
- registrar `ERRO_PROCESSAMENTO` recebido por evento;
- registrar `ERRO_PERSISTENCIA`;
- atualizar a foto atual do usuário após sucesso.

---

# 4. Stack e versões

| Área | Tecnologia |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.0 |
| Build | Maven 3.9.12 via Maven Wrapper |
| Banco local | MySQL 8.4 LTS |
| Banco GCP | Cloud SQL for MySQL |
| Migrações | Flyway |
| Testes unitários | JUnit 5 + Mockito |
| Testes de integração | Testcontainers + MySQL |
| Cobertura | JaCoCo |
| Object Storage local | fake-gcs-server |
| Object Storage GCP | Google Cloud Storage |
| Mensageria local | Google Cloud Pub/Sub Emulator |
| Mensageria GCP | Google Cloud Pub/Sub |
| Function local | Functions Framework |
| Function GCP | Cloud Run Functions |
| Containers | Docker Engine + Docker Compose |
| Testes funcionais da API | Postman |

Usar Maven Wrapper. Não depender de versão global diferente de Maven.

---

# 5. Organização do backend

Utilizar **Package by Feature**.

Exemplo conceitual do `photo-api`:

```text
photo-api/
└── src/main/java/.../
    ├── usuario/
    │   ├── controller/
    │   ├── service/
    │   ├── domain/
    │   ├── repository/
    │   ├── request/
    │   └── response/
    ├── foto/
    │   ├── controller/
    │   ├── service/
    │   ├── domain/
    │   ├── repository/
    │   ├── request/
    │   └── response/
    ├── storage/
    ├── config/
    └── exception/
```

Exemplo conceitual do `photo-consumer`:

```text
photo-consumer/
└── src/main/java/.../
    ├── processamento/
    │   ├── consumer/
    │   ├── service/
    │   ├── domain/
    │   ├── repository/
    │   └── event/
    ├── storage/
    ├── config/
    └── exception/
```

Fluxo interno esperado:

```text
Controller
    ↓
Service
    ↓
Repository
```

Controllers não acessam repositories diretamente.

Não implementar Clean Architecture completa ou arquitetura hexagonal completa sem necessidade explícita.

---

# 6. SOLID com pragmatismo

Aplicar SOLID quando melhorar:

- coesão;
- baixo acoplamento;
- testabilidade;
- legibilidade;
- manutenção;
- extensibilidade.

Não criar interfaces, adapters, factories ou camadas apenas para cumprir formalmente SOLID.

Orientações:

- SRP: cada classe com responsabilidade clara;
- OCP: criar extensão somente quando houver variação real;
- LSP: implementações de abstrações devem respeitar o mesmo contrato;
- ISP: interfaces pequenas e específicas;
- DIP: utilizar abstrações principalmente nas fronteiras externas quando isso trouxer benefício concreto.

Exemplo válido:

```text
FotoService
    ↓
StorageService
    ↓
GoogleCloudStorageService
```

Não criar `UsuarioServiceInterface` apenas porque existe `UsuarioService`.

---

# 7. Java Records

Preferir Records para estruturas imutáveis de transporte:

- requests;
- responses;
- eventos;
- mensagens Pub/Sub;
- contratos de integração.

Não utilizar Records automaticamente para:

- entidades JPA;
- objetos de domínio mutáveis;
- objetos com comportamento relevante.

Evitar pacote genérico `dto/`. Preferir:

```text
request/
response/
event/
```

---

# 8. API mínima

## 8.1 Cadastrar usuário

```http
POST /api/v1/usuarios
Content-Type: multipart/form-data
```

Partes obrigatórias:

```text
nome = João da Silva
foto = <arquivo JPG/JPEG/PNG>
```

A foto é **obrigatória**.

Não permitir criação sem imagem.

Antes de criar o usuário, validar:

- nome;
- presença da foto;
- formato;
- tamanho máximo.

Resposta conceitual:

```json
{
  "id": 1,
  "nome": "João da Silva",
  "processamentoId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSANDO"
}
```

O `usuarioId` deve ser gerado pelo backend/banco.

O cadastro somente é considerado aceito após:

1. validação do nome;
2. validação da imagem;
3. persistência do usuário;
4. criação do processamento inicial;
5. armazenamento da imagem original;
6. atualização do processamento para `PROCESSANDO`.

Falhas anteriores a esse ponto não podem deixar usuário cadastrado sem imagem inicial aceita.

## 8.2 Listar usuários

```http
GET /api/v1/usuarios
```

Retornar no mínimo:

- ID;
- nome;
- referência/estado da foto atual.

## 8.3 Consultar usuário

```http
GET /api/v1/usuarios/{usuarioId}
```

## 8.4 Atualizar nome

```http
PUT /api/v1/usuarios/{usuarioId}
Content-Type: application/json
```

```json
{
  "nome": "João de Souza"
}
```

O ID vem exclusivamente pelo path e é imutável.

## 8.5 Excluir usuário

```http
DELETE /api/v1/usuarios/{usuarioId}
```

Se existir processamento ativo:

```http
409 Conflict
```

Nenhum dado deverá ser excluído nesse caso.

## 8.6 Foto atual

```http
GET /api/v1/usuarios/{usuarioId}/foto
```

Retornar a imagem processada atualmente associada ao usuário quando existir.

## 8.7 Atualização posterior da foto

```http
POST /api/v1/usuarios/{usuarioId}/fotos
Content-Type: multipart/form-data
```

Essa operação não faz parte do cadastro inicial.

Responsabilidades:

- validar usuário;
- validar imagem;
- verificar se já existe processamento ativo;
- gerar novo `processamentoId`;
- registrar processamento;
- armazenar original;
- alterar para `PROCESSANDO`;
- iniciar fluxo assíncrono.

Se já existir processamento ativo:

```http
409 Conflict
```

## 8.8 Consultar processamento

```http
GET /api/v1/processamentos/{processamentoId}
```

Resposta conceitual:

```json
{
  "processamentoId": "550e8400-e29b-41d4-a716-446655440000",
  "usuarioId": 1,
  "status": "PROCESSADA"
}
```

---

# 9. Regras de negócio

## RN01 — Foto obrigatória no cadastro

`nome` e `foto` são obrigatórios.

Cadastro sem foto deverá ser rejeitado sem persistir usuário.

## RN02 — ID imutável

O `usuarioId`:

- é gerado automaticamente;
- nunca é digitado pelo cliente;
- não pode ser alterado;
- não deve ser reutilizado intencionalmente após exclusão.

## RN03 — Formatos

Aceitar:

- JPG;
- JPEG;
- PNG.

## RN04 — Tamanho máximo

Máximo inicial:

```text
10 MB
```

O limite deve ser configurável.

## RN05 — Uma imagem por requisição

Não aceitar múltiplos arquivos na mesma operação.

## RN06 — Original preservado

A imagem original nunca é alterada pelo processamento.

## RN07 — Buckets separados

Usar:

```text
fotos-usuarios-original
fotos-usuarios-processadas
```

Isso evita loop de processamento.

## RN08 — Processamento assíncrono

A requisição HTTP não aguarda todo o processamento.

## RN09 — Identificação do processamento

Cada upload aceito gera UUID único `processamentoId`.

## RN10 — Pub/Sub não transporta imagem

Transportar somente referência e metadados.

Evento conceitual:

```json
{
  "processamentoId": "550e8400-e29b-41d4-a716-446655440000",
  "usuarioId": 1,
  "bucket": "fotos-usuarios-processadas",
  "arquivo": "1/550e8400/foto.jpg",
  "status": "PROCESSADA"
}
```

## RN11 — photo-processor publica Pub/Sub

Após processar:

```text
photo-processor
      ↓
salva imagem processada
      ↓
publica resultado no Pub/Sub
```

Nunca publicar evento de sucesso antes do armazenamento da imagem processada terminar.

## RN12 — Foto atual

Um usuário pode possuir histórico de vários processamentos, mas apenas uma foto é atual.

A nova foto somente passa a ser atual após `PERSISTIDA`.

## RN13 — Atualização da foto

Durante atualização:

- foto anterior permanece atual;
- novo processamento recebe novo UUID;
- sucesso `PERSISTIDA` troca a foto;
- erro mantém foto anterior.

## RN14 — Um processamento ativo por usuário

Estados ativos:

```text
RECEBIDA
PROCESSANDO
PROCESSADA
PERSISTINDO
```

Enquanto existir um desses estados, rejeitar novo upload com:

```http
409 Conflict
```

Estados terminais:

```text
PERSISTIDA
ERRO_PROCESSAMENTO
ERRO_PERSISTENCIA
```

## RN15 — Exclusão bloqueada durante processamento

Se houver processamento ativo:

```http
DELETE → 409 Conflict
```

## RN16 — Exclusão

Quando permitida, remover:

- usuário;
- processamentos associados;
- imagem persistida;
- objetos originais e processados relacionados.

## RN17 — Idempotência

Eventos duplicados não podem:

- gerar persistência duplicada;
- criar registros duplicados;
- trocar novamente a foto atual;
- corromper estados.

## RN18 — Falha de processamento

Em falha:

- manter imagem original;
- registrar erro;
- usar `ERRO_PROCESSAMENTO`;
- não executar Retry automático.

## RN19 — Falha de persistência

Em falha:

- manter imagem processada;
- não reprocessar original;
- registrar `ERRO_PERSISTENCIA`;
- não executar Retry automático.

---

# 10. Estados

```text
RECEBIDA
PROCESSANDO
PROCESSADA
PERSISTINDO
PERSISTIDA
ERRO_PROCESSAMENTO
ERRO_PERSISTENCIA
```

Fluxo:

```text
RECEBIDA
   ↓
PROCESSANDO
   ↓
PROCESSADA
   ↓
PERSISTINDO
   ↓
PERSISTIDA
```

Responsabilidades:

```text
photo-api
   ├── RECEBIDA
   └── PROCESSANDO

photo-processor
   └── publica PROCESSADA ou ERRO_PROCESSAMENTO

photo-consumer
   ├── PROCESSADA
   ├── PERSISTINDO
   ├── PERSISTIDA
   ├── ERRO_PROCESSAMENTO
   └── ERRO_PERSISTENCIA
```

`photo-processor` não acessa o MySQL.

---

# 11. Banco de dados

## Local

```text
MySQL 8.4 LTS
Docker
```

## GCP

```text
Cloud SQL for MySQL
```

Não executar MySQL manualmente em:

- Compute Engine;
- GKE.

Usar Flyway para versionamento do schema.

Modelo conceitual:

```text
USUARIO
- id BIGINT
- nome VARCHAR
- foto_atual_processamento_id nullable
- data_cadastro
- data_atualizacao

PROCESSAMENTO_FOTO
- id UUID
- usuario_id FK
- status
- arquivo_original
- arquivo_processado
- imagem_processada BLOB nullable
- content_type
- nome_arquivo
- data_recebimento
- data_processamento
- data_persistencia
- erro
```

Relação:

```text
USUARIO 1 ─── N PROCESSAMENTO_FOTO
```

---

# 12. Ambiente local

Executar inicialmente sem dependência obrigatória de conta GCP.

```text
Docker Compose
├── mysql
├── fake-gcs-server
├── pubsub-emulator
├── photo-api
├── photo-processor
└── photo-consumer
```

## 12.1 Gatilho local

No ambiente local:

```text
photo-api
   ↓ armazena original
fake-gcs-server
   ↓
photo-api aciona trigger local de forma assíncrona
   ↓
photo-processor / Functions Framework
```

O acionamento local:

- não pode bloquear a resposta do upload;
- deve ficar isolado por configuração/adaptador de ambiente;
- deve reproduzir os dados essenciais do evento que existirá no GCP.

## 12.2 Gatilho GCP

No GCP:

```text
Cloud Storage
    ↓ evento de criação
Cloud Run Function
```

---

# 13. Evolução para Google Cloud

Mapeamento:

| Local | GCP |
|---|---|
| MySQL Docker | Cloud SQL for MySQL |
| fake-gcs-server | Google Cloud Storage |
| Pub/Sub Emulator | Google Cloud Pub/Sub |
| Functions Framework | Cloud Run Functions |
| Spring Boot local/container | Compute Engine e posteriormente GKE |

Cloud Storage, Pub/Sub, Cloud Run Functions e Cloud SQL permanecem serviços gerenciados fora do GKE.

---

# 14. Requisitos não funcionais

- processamento assíncrono;
- idempotência;
- logs com `processamentoId`;
- configuração externa;
- segredos fora do Git;
- diferenças local/GCP tratadas por configuração;
- simplicidade;
- baixo acoplamento;
- sem Retry automático;
- sem Circuit Breaker;
- sem mecanismo de Timeout como estratégia de resiliência.

---

# 15. Segurança fora do escopo

Não implementar:

- login;
- Spring Security;
- JWT;
- OAuth2;
- autenticação;
- autorização;
- roles;
- senha;
- sessão;
- refresh token.

O usuário do domínio não representa uma conta autenticada.

---

# 16. Testes automatizados

## photo-api

Cobrir:

- cadastro com nome + foto;
- cadastro sem foto;
- imagem com formato inválido;
- imagem acima de 10 MB;
- listagem;
- consulta;
- atualização de nome;
- ID imutável;
- exclusão;
- bloqueio de exclusão;
- atualização de foto;
- bloqueio de segundo processamento;
- foto atual;
- consulta de status.

## photo-processor

Cobrir:

- processamento;
- entrada inválida;
- armazenamento da processada;
- publicação Pub/Sub;
- falha de processamento.

## photo-consumer

Cobrir:

- consumo;
- estados;
- persistência;
- idempotência;
- falha de persistência;
- evento de erro.

Meta inicial de cobertura de linhas:

```text
80%
```

Usar JaCoCo.

---

# 17. Validação funcional com Postman

O Postman será o cliente oficial de validação funcional da Fase 1.

Criar no repositório:

```text
postman/
├── async-photo-processing-platform.postman_collection.json
└── local.postman_environment.json
```

O environment deverá permitir no mínimo:

```text
baseUrl
usuarioId
processamentoId
```

Não armazenar segredos reais.

## 17.1 Cenários mínimos

### Cenário A — cadastro sem foto

```text
POST /api/v1/usuarios
nome presente
foto ausente
```

Esperado:

- requisição rejeitada;
- nenhum usuário criado.

### Cenário B — cadastro válido

Enviar:

```text
nome + foto JPG/JPEG/PNG
```

Validar:

- retorno do `usuarioId`;
- retorno do `processamentoId`;
- status inicial;
- consulta posterior do processamento.

### Cenário C — formato inválido

Enviar arquivo não suportado.

Esperado: rejeição.

### Cenário D — arquivo acima do limite

Esperado: rejeição.

### Cenário E — listar usuários

```http
GET /api/v1/usuarios
```

### Cenário F — consultar usuário

```http
GET /api/v1/usuarios/{usuarioId}
```

### Cenário G — atualizar nome

```http
PUT /api/v1/usuarios/{usuarioId}
```

Confirmar que o ID permanece igual.

### Cenário H — acompanhar processamento

Consultar repetidamente:

```http
GET /api/v1/processamentos/{processamentoId}
```

até estado terminal.

### Cenário I — recuperar foto atual

Após `PERSISTIDA`:

```http
GET /api/v1/usuarios/{usuarioId}/foto
```

### Cenário J — atualizar foto

```http
POST /api/v1/usuarios/{usuarioId}/fotos
```

Validar que a foto anterior permanece atual até novo `PERSISTIDA`.

### Cenário K — segundo upload concorrente

Com processamento ativo, enviar nova foto.

Esperado:

```http
409 Conflict
```

### Cenário L — exclusão durante processamento

Esperado:

```http
409 Conflict
```

### Cenário M — exclusão após estado terminal

Esperado:

- exclusão bem-sucedida;
- usuário não localizado posteriormente;
- dados associados removidos conforme regra.

---

# 18. Critérios de conclusão da Fase 1

O backend somente será considerado concluído quando:

1. os três componentes backend estiverem implementados;
2. MySQL local estiver funcionando;
3. Flyway estiver funcionando;
4. fake-gcs-server estiver funcionando;
5. Pub/Sub Emulator estiver funcionando;
6. Functions Framework estiver funcionando;
7. cadastro exigir nome + foto;
8. processamento assíncrono funcionar ponta a ponta;
9. `photo-processor` publicar diretamente no Pub/Sub;
10. `photo-consumer` persistir a imagem;
11. estados forem atualizados corretamente;
12. idempotência estiver implementada;
13. bloqueio de processamento concorrente funcionar;
14. bloqueio de exclusão durante processamento funcionar;
15. testes automatizados relevantes passarem;
16. JaCoCo atingir a meta definida;
17. todos os cenários mínimos da coleção Postman forem validados;
18. nenhuma implementação de frontend tiver sido introduzida nesta fase.

---

# 19. Ordem de implementação para o Codex

```text
1. Estrutura do repositório backend
↓
2. Java 25 + Spring Boot 4.0.0 + Maven Wrapper 3.9.12
↓
3. MySQL 8.4 + Flyway
↓
4. Modelo USUARIO + PROCESSAMENTO_FOTO
↓
5. photo-api
↓
6. Cadastro nome + foto
↓
7. CRUD de usuário
↓
8. Upload posterior de foto
↓
9. Regras 409
↓
10. fake-gcs-server
↓
11. Armazenamento original
↓
12. Estados RECEBIDA → PROCESSANDO
↓
13. photo-processor + Functions Framework
↓
14. Trigger local assíncrono
↓
15. Processamento + armazenamento processado
↓
16. Pub/Sub Emulator
↓
17. Publicação pelo photo-processor
↓
18. photo-consumer
↓
19. Persistência + estados finais
↓
20. Foto atual
↓
21. Idempotência
↓
22. Tratamento básico de falhas
↓
23. Testes automatizados
↓
24. Collection Postman
↓
25. Validação funcional completa com Postman
```

---

# 20. Instruções finais ao Codex

- implementar somente backend nesta fase;
- não criar frontend;
- não criar Vite;
- não criar telas;
- não adicionar autenticação;
- utilizar Package by Feature;
- aplicar SOLID com pragmatismo;
- utilizar Records quando adequados;
- evitar overengineering;
- não criar abstrações sem necessidade;
- manter `photo-processor` sem acesso ao banco;
- manter Pub/Sub somente com referências/metadados;
- exigir foto no cadastro;
- preservar ID imutável;
- rejeitar concorrência com HTTP 409;
- utilizar Postman como validação funcional da API;
- não iniciar a Fase 2 até os critérios de conclusão da Fase 1 serem atendidos.
