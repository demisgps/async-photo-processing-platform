# Tasks: Backend de Processamento Assíncrono de Fotos

**Input**: documentos de desenho em `specs/001-backend-photo-processing/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `contracts/events.md`, `quickstart.md` e `.specify/memory/constitution.md`

**Tests**: testes unitários, de integração, contrato e concorrência são obrigatórios e aparecem junto da funcionalidade correspondente. Em cada bloco, escrever o teste e confirmar a falha esperada antes da implementação.

**Execução dos testes**: todo teste Java listado sob `src/test/java` deve ser autocontido e executado por Surefire/Failsafe em `./mvnw verify`, provisionando dependências externas com Testcontainers quando necessário, sem exigir `docker compose` pré-iniciado. Validações que dependem do ambiente Compose são scripts/roteiros locais fora do lifecycle Maven e só são executadas após T141.

**Organization**: tarefas agrupadas por fundação e por User Story, em ordem de dependência. `[P]` indica execução paralela segura em arquivos distintos; `[USn]` mantém rastreabilidade com `spec.md`.

## Formato: `[ID] [P?] [Story] Descrição com caminho`

- **[P]**: executável em paralelo, sem dependência pendente nem conflito de arquivo
- **[Story]**: User Story coberta (`US1` a `US6`)
- Cada tarefa produz um resultado objetivamente verificável no caminho indicado

---

## Phase 1: Fundação do repositório backend

**Purpose**: estabelecer o build Backend First e os três módulos, sem código funcional ou frontend.

- [ ] T001 Criar o agregador Maven Java 25 com os módulos `photo-api`, `photo-consumer` e `photo-processor` em `backend/pom.xml`
- [ ] T002 Configurar Maven Wrapper 3.9.12 em `backend/mvnw`, `backend/mvnw.cmd` e `backend/.mvn/wrapper/maven-wrapper.properties`
- [ ] T003 [P] Criar o módulo Spring Boot 4.0.0 `photo-api` e sua estrutura inicial de testes em `backend/photo-api/pom.xml` e `backend/photo-api/src/test/java/`
- [ ] T004 [P] Criar o módulo Spring Boot 4.0.0 `photo-consumer` e sua estrutura inicial de testes em `backend/photo-consumer/pom.xml` e `backend/photo-consumer/src/test/java/`
- [ ] T005 [P] Criar o módulo Java 25 Functions Framework, sem Spring Boot e sem Actuator, em `backend/photo-processor/pom.xml` e `backend/photo-processor/src/test/java/`
- [ ] T006 Configurar compiler, Surefire, Failsafe e JaCoCo com mínimo de 80% de linhas por módulo e agregado em `backend/pom.xml`
- [ ] T007 [P] Adicionar Actuator ao `photo-api` e expor somente health, liveness e readiness em `backend/photo-api/pom.xml` e `backend/photo-api/src/main/resources/application.yml`
- [ ] T008 [P] Adicionar Actuator ao `photo-consumer` e expor somente health, liveness e readiness em `backend/photo-consumer/pom.xml` e `backend/photo-consumer/src/main/resources/application.yml`
- [ ] T009 Executar `./mvnw verify` em `backend/` e confirmar módulos, Java 25, testes vazios e quality gates sem criar frontend

**Checkpoint**: build multimódulo reproduzível; Actuator existe somente nos dois serviços Spring Boot.

---

## Phase 2: Infraestrutura e persistência compartilhadas

**Purpose**: preparar dependências locais, contratos internos e persistência que bloqueiam todas as histórias.

**⚠️ CRITICAL**: nenhuma User Story começa antes deste checkpoint.

### Infraestrutura local

- [ ] T010 [P] Definir MySQL 8.4 LTS, fake-gcs-server, Pub/Sub Emulator e redes/volumes locais em `compose.yaml`
- [ ] T011 [P] Criar bootstrap idempotente dos buckets `fotos-usuarios-original` e `fotos-usuarios-processadas` em `docker/fake-gcs-server/bootstrap.sh`
- [ ] T012 [P] Criar bootstrap idempotente de `foto-processada`, `photo-consumer-sub`, `foto-processada-dlq` e `photo-consumer-dlq-sub`, configurando na subscription macro-redelivery de 10–300s, máximo de 8 entregas best-effort, DLT e retenção de 7 dias em `docker/pubsub/bootstrap.sh`
- [ ] T013 [P] Criar o Dockerfile local do `photo-api` em `backend/photo-api/Dockerfile`
- [ ] T014 [P] Criar o Dockerfile local do `photo-consumer` em `backend/photo-consumer/Dockerfile`
- [ ] T015 [P] Criar o Dockerfile local do Functions Framework do `photo-processor` em `backend/photo-processor/Dockerfile`
- [ ] T016 [P] Criar o Dockerfile local do `storage-event-dispatcher` em `docker/storage-event-dispatcher/Dockerfile`
- [ ] T017 Preparar o `storage-event-dispatcher` infraestrutural em `docker/storage-event-dispatcher/`, sem regra de negócio, para construir o CloudEvent de finalização definido em `contracts/events.md`
- [ ] T018 Integrar no `compose.yaml` as imagens locais da API, consumer, processor e dispatcher, deixando o wiring dispatcher → Functions Framework configurado sem exigir validação funcional antes da `PhotoProcessorFunction`
- [ ] T019 Criar verificação local de bootstrap repetível de buckets, tópico, subscriptions e DLT em `docker/tests/bootstrap-local.sh`, classificada fora do Maven e executada somente com o ambiente Compose já iniciado

### Modelo relacional e acesso a dados

- [ ] T020 [P] Configurar datasource MySQL e Flyway no `photo-api` em `backend/photo-api/src/main/resources/application.yml`
- [ ] T021 [P] Configurar datasource MySQL do `photo-consumer` para o schema versionado pelas migrations Flyway da feature em `backend/photo-consumer/src/main/resources/application.yml`
- [ ] T022 Criar `USUARIO` com ID gerado pelo banco, nome, foto atual, `proxima_sequencia_upload`, versionamento otimista e datas em `backend/photo-api/src/main/resources/db/migration/V1__create_usuario_and_processamento_foto.sql`
- [ ] T023 Completar a mesma migration com `PROCESSAMENTO_FOTO`, UUID, FKs de existência, sequência, referências/metadados, BLOB, erro, versão, índices e exatamente os sete estados aprovados em `backend/photo-api/src/main/resources/db/migration/V1__create_usuario_and_processamento_foto.sql`
- [ ] T024 Criar unicidade `(usuario_id, sequencia_upload)` e proteção de processamento ativo por `usuario_ativo_id` em `backend/photo-api/src/main/resources/db/migration/V2__add_active_processing_guard.sql`
- [ ] T025 [P] Criar entidades JPA não-Record e enum com somente `RECEBIDA`, `PROCESSANDO`, `PROCESSADA`, `PERSISTINDO`, `PERSISTIDA`, `ERRO_PROCESSAMENTO` e `ERRO_PERSISTENCIA` em `backend/photo-api/src/main/java/com/example/photoapi/processamento/domain/`
- [ ] T026 [P] Criar o mapeamento JPA equivalente do consumidor em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/domain/`
- [ ] T027 [P] Criar repositories de usuário/processamento, incluindo consultas com lock e updates CAS, em `backend/photo-api/src/main/java/com/example/photoapi/usuario/repository/` e `backend/photo-api/src/main/java/com/example/photoapi/processamento/repository/`
- [ ] T028 [P] Criar repository do processamento para consumo, retomada e promoção condicional em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/repository/`
- [ ] T029 Criar testes Testcontainers MySQL 8.4 para migrations, PKs, FKs, CHECKs, índices e enum de estados em `backend/photo-api/src/test/java/com/example/photoapi/persistence/MigrationIT.java`
- [ ] T030 Criar testes Testcontainers para processamento ativo único, corrida de alocação de `sequencia_upload` e versionamento/CAS em `backend/photo-api/src/test/java/com/example/photoapi/persistence/ProcessingConstraintIT.java`

### Contratos e configuração comuns

- [ ] T031 [P] Criar Records de requests, responses e erros aderentes a `contracts/openapi.yaml` em `backend/photo-api/src/main/java/com/example/photoapi/usuario/web/contract/`
- [ ] T032 [P] Criar Records de `PhotoProcessingResult` e `PhotoProcessingError` aderentes a `contracts/events.md` no processor em `backend/photo-processor/src/main/java/com/example/photoprocessor/event/`
- [ ] T033 [P] Criar Records equivalentes e validação de `schemaVersion` no consumer em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/event/`
- [ ] T034 [P] Configurar propriedades tipadas de buckets, Pub/Sub e limites operacionais no `photo-api` em `backend/photo-api/src/main/java/com/example/photoapi/config/`
- [ ] T035 [P] Configurar propriedades tipadas equivalentes no consumer em `backend/photo-consumer/src/main/java/com/example/photoconsumer/config/`
- [ ] T036 [P] Configurar propriedades tipadas equivalentes no processor em `backend/photo-processor/src/main/java/com/example/photoprocessor/config/`

**Checkpoint**: containers, configuração e wiring local estão preparados; schema é validado em MySQL 8.4 e contratos-base compilam. A entrega funcional dispatcher → `PhotoProcessorFunction` permanece deliberadamente adiada até a US2.

---

## Phase 3: User Story 1 — Cadastrar usuário com foto (Priority: P1) 🎯 MVP

**Goal**: aceitar nome e exatamente uma imagem válida, persistir o aceite e retornar `201` com `PROCESSANDO`.

**Independent Test**: enviar multipart de cadastro e verificar IDs gerados, original armazenada e resposta `201`; exercitar `400`, `413` e `415` sem aceite parcial.

### Tests for User Story 1

- [ ] T037 [P] [US1] Criar testes unitários de trim, contagem Unicode de 2–150 caracteres e nome inválido em `backend/photo-api/src/test/java/com/example/photoapi/usuario/validation/NomeValidatorTest.java`
- [ ] T038 [P] [US1] Criar testes unitários de tamanho, magic bytes/assinatura, decodificação válida e extensão normalizada derivada de JPG/JPEG/PNG, incluindo conteúdo corrompido com assinatura válida, exatamente 10.485.760 bytes e excesso em `backend/photo-api/src/test/java/com/example/photoapi/foto/validation/PhotoValidatorTest.java`
- [ ] T039 [P] [US1] Criar testes HTTP para foto ausente, partes `foto` repetidas e matriz `201/400/413/415` em `backend/photo-api/src/test/java/com/example/photoapi/usuario/web/CreateUsuarioControllerIT.java`
- [ ] T040 [US1] Criar teste de integração do aceite consistente sem aceite parcial entre MySQL e Storage, incluindo sequência inicial, chave determinística e limpeza/compensação simples antes da resposta HTTP em `backend/photo-api/src/test/java/com/example/photoapi/usuario/service/CreateUsuarioServiceIT.java`

### Implementation for User Story 1

- [ ] T041 [P] [US1] Implementar validação do nome por caracteres Unicode após trim em `backend/photo-api/src/main/java/com/example/photoapi/usuario/validation/NomeValidator.java`
- [ ] T042 [P] [US1] Implementar validação pré-aceite por tamanho máximo configurável de 10.485.760 bytes, magic bytes e decodificação completa como JPG/JPEG ou PNG em `backend/photo-api/src/main/java/com/example/photoapi/foto/validation/PhotoValidator.java`
- [ ] T043 [P] [US1] Implementar cliente do bucket original com chave `{usuarioId}/{processamentoId}/arquivo.<ext>`, extensão normalizada pelo formato validado, precondição create-only e exclusão idempotente em `backend/photo-api/src/main/java/com/example/photoapi/storage/OriginalPhotoStorage.java`
- [ ] T044 [US1] Implementar serviço de cadastro com `usuarioId` gerado pelo banco, `processamentoId` UUID, sequência 1, transições `RECEBIDA` → `PROCESSANDO` e aceite consistente sem resposta parcial, usando limpeza/compensação simples antes do `201` quando necessária, em `backend/photo-api/src/main/java/com/example/photoapi/usuario/service/CreateUsuarioService.java`
- [ ] T045 [US1] Implementar rejeição explícita de multipart repetido e `POST /api/v1/usuarios` em `backend/photo-api/src/main/java/com/example/photoapi/usuario/web/UsuarioController.java`
- [ ] T046 [US1] Mapear erros do cadastro para o modelo comum e somente `201/400/413/415/5xx` aplicáveis em `backend/photo-api/src/main/java/com/example/photoapi/exception/ApiExceptionHandler.java`
- [ ] T047 [US1] Criar testes de timeout, retry finito/classificação e estados OPEN, HALF_OPEN e CLOSED do Circuit Breaker seletivo do Storage em `backend/photo-api/src/test/java/com/example/photoapi/storage/StorageResilienceTest.java`
- [ ] T048 [US1] Configurar no client Storage da API os timeouts, máximo de tentativas, backoff exponencial e jitter definidos no plano, sem repetir erros funcionais/não transitórios, em `backend/photo-api/src/main/java/com/example/photoapi/config/StorageClientConfig.java`
- [ ] T049 [US1] Integrar Resilience4j 2.4.0 somente ao Circuit Breaker síncrono do Storage da API e excluir validações/4xx de sua métrica em `backend/photo-api/pom.xml` e `backend/photo-api/src/main/java/com/example/photoapi/config/StorageResilienceConfig.java`
- [ ] T050 [US1] Verificar a US1 executando seus testes unitários, de integração e de resiliência no módulo `backend/photo-api/`

**Checkpoint**: cadastro funciona isoladamente e nunca aceita usuário sem exatamente uma foto válida.

---

## Phase 4: User Story 2 — Acompanhar processamento e consultar foto atual (Priority: P1)

**Goal**: executar o caminho assíncrono de sucesso, permitir consulta do processamento e servir a foto somente após `PERSISTIDA`.

**Independent Test**: cadastrar usuário, entregar CloudEvent, consumir resultado e verificar a sequência completa até `PERSISTIDA`, consulta do status e bytes da foto atual.

### photo-processor e testes da transformação

- [ ] T051 [P] [US2] Criar testes de contrato do CloudEvent para bucket, name, generation, `usuarioId` e `processamentoId` em `backend/photo-processor/src/test/java/com/example/photoprocessor/processamento/StorageCloudEventTest.java`
- [ ] T052 [P] [US2] Criar fixtures e testes de orientação EXIF, limite 1024x1024, proporção, ausência de upscale, JPEG e PNG com alpha em `backend/photo-processor/src/test/java/com/example/photoprocessor/imagem/ImageTransformerTest.java`
- [ ] T053 [P] [US2] Integrar Thumbnailator 0.4.21 e TwelveMonkeys ImageIO JPEG em `backend/photo-processor/pom.xml`
- [ ] T054 [US2] Implementar transformação de imagem com as invariantes aprovadas em `backend/photo-processor/src/main/java/com/example/photoprocessor/imagem/ImageTransformer.java`
- [ ] T055 [P] [US2] Implementar acesso aos buckets original/processado com chave `{usuarioId}/{processamentoId}/arquivo.<ext>` normalizada pelo formato validado, HEAD e create-only por generation match em `backend/photo-processor/src/main/java/com/example/photoprocessor/storage/PhotoStorage.java`
- [ ] T056 [P] [US2] Implementar publicação sem bytes de `PhotoProcessingResult` e `PhotoProcessingError` em `backend/photo-processor/src/main/java/com/example/photoprocessor/event/ProcessingEventPublisher.java`
- [ ] T057 [US2] Criar testes unitários da orquestração básica CloudEvent → obter/validar original → transformar → salvar processada → publicar `PhotoProcessingResult`, e da publicação de `PhotoProcessingError` em erro funcional definitivo, em `backend/photo-processor/src/test/java/com/example/photoprocessor/processamento/PhotoProcessingServiceTest.java`
- [ ] T058 [US2] Implementar a orquestração básica no `PhotoProcessingService`, obtendo/validando a original, transformando, salvando a processada e publicando `PhotoProcessingResult`, ou `PhotoProcessingError` para erro funcional definitivo, em `backend/photo-processor/src/main/java/com/example/photoprocessor/processamento/PhotoProcessingService.java`
- [ ] T059 [US2] Implementar `CloudEventFunction` aceitando somente finalização do bucket original, ignorando eventos incompatíveis com log rastreável e delegando o fluxo válido ao `PhotoProcessingService` em `backend/photo-processor/src/main/java/com/example/photoprocessor/processamento/PhotoProcessorFunction.java`
- [ ] T060 [US2] Criar testes de contrato garantindo que o processor não acessa MySQL e publica somente IDs, referências e metadados em `backend/photo-processor/src/test/java/com/example/photoprocessor/event/ProcessingEventContractTest.java`
- [ ] T061 [US2] Criar teste autocontido no Failsafe, com dependências próprias via Testcontainers e sem Compose pré-iniciado, validando dispatcher → Functions Framework → `PhotoProcessorFunction` em `backend/photo-processor/src/test/java/com/example/photoprocessor/processamento/DispatcherCloudEventIT.java`

### photo-consumer e persistência final

- [ ] T062 [P] [US2] Criar testes de desserialização e validação sem bytes para `PhotoProcessingResult` e `PhotoProcessingError`, incluindo payload malformado, `schemaVersion` incompatível, IDs ausentes e referência estrutural inválida em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/event/ProcessingEventTest.java`
- [ ] T063 [US2] Criar testes Testcontainers do `PhotoProcessingResult` com transições condicionais e monotônicas `PROCESSANDO` → `PROCESSADA` → `PERSISTINDO` → `PERSISTIDA`, BLOB/metadados e promoção no mesmo commit em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/FinalizeProcessingIT.java`
- [ ] T064 [US2] Criar testes Testcontainers do `PhotoProcessingError` com transição condicional `PROCESSANDO` → `ERRO_PROCESSAMENTO` e ACK/no-op rastreável para erro atrasado após estado terminal em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/ProcessingErrorIT.java`
- [ ] T065 [US2] Implementar subscriber de `photo-consumer-sub` que ACKa no-op válido duplicado/atrasado/fora de ordem/terminal, mas falha/NACKa payload malformado ou contratualmente inválido para redelivery finita e DLT em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/consumer/PhotoResultSubscriber.java`
- [ ] T066 [US2] Implementar download da imagem processada fora da transação longa em `backend/photo-consumer/src/main/java/com/example/photoconsumer/storage/ProcessedPhotoStorage.java`
- [ ] T067 [US2] Implementar tratamento de `PhotoProcessingResult` que realiza condicionalmente `PROCESSANDO` → `PROCESSADA` → `PERSISTINDO`, valida pertencimento/elegibilidade, persiste BLOB/metadados, promove a foto atual e conclui `PERSISTIDA` no mesmo commit em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/FinalizeProcessingService.java`
- [ ] T068 [US2] Implementar tratamento de `PhotoProcessingError` com transição condicional e monotônica `PROCESSANDO` → `ERRO_PROCESSAMENTO`, sem regressão de estado terminal, em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/ProcessingErrorService.java`
- [ ] T069 [US2] Impedir `PERSISTIDA` quando a promoção falhar e cobrir rollback integral em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/PromotionAtomicityIT.java`

### Consultas da história

- [ ] T070 [P] [US2] Criar testes HTTP de consulta de processamento `200/404` em `backend/photo-api/src/test/java/com/example/photoapi/processamento/web/ProcessingQueryControllerIT.java`
- [ ] T071 [P] [US2] Criar testes HTTP da foto atual para `200`, `409` temporário e `404` após erro terminal sem foto em `backend/photo-api/src/test/java/com/example/photoapi/foto/web/CurrentPhotoControllerIT.java`
- [ ] T072 [US2] Implementar consulta por `processamentoId` em `backend/photo-api/src/main/java/com/example/photoapi/processamento/service/ProcessingQueryService.java` e `backend/photo-api/src/main/java/com/example/photoapi/processamento/web/ProcessingController.java`
- [ ] T073 [US2] Implementar consulta da foto atual com Content-Type JPEG/PNG e sem promoção antecipada em `backend/photo-api/src/main/java/com/example/photoapi/foto/service/CurrentPhotoService.java` e `backend/photo-api/src/main/java/com/example/photoapi/foto/web/PhotoController.java`
- [ ] T074 [US2] Criar teste ponta a ponta autocontido no Failsafe da API, provisionando suas dependências via Testcontainers e sem Compose pré-iniciado, para API → Storage → dispatcher → Function → Pub/Sub → consumer → MySQL em `backend/photo-api/src/test/java/com/example/photoapi/e2e/HappyPathIT.java`

**Checkpoint**: o fluxo assíncrono feliz e as consultas da US2 funcionam ponta a ponta.

---

## Phase 5: User Story 6 — Idempotência, falhas e resiliência (Priority: P1)

**Goal**: tornar processor e consumer seguros sob retry, redelivery, concorrência, indisponibilidade e mensagens fora de ordem.

**Independent Test**: repetir e reordenar o mesmo processamento, injetar falhas externas e confirmar efeitos únicos, estados monotônicos, budgets finitos e DLT rastreável.

### Reexecução segura e resiliência do photo-processor

Estas tarefas endurecem o `PhotoProcessingService` básico criado na T058; não criam uma segunda orquestração.

- [ ] T075 [P] [US6] Criar testes para HEAD, metadados coincidentes, corrida create-only e reutilização do objeto processado em `backend/photo-processor/src/test/java/com/example/photoprocessor/processamento/ProcessorIdempotencyTest.java`
- [ ] T076 [P] [US6] Criar testes de timeout, retry finito com backoff/jitter e não-retry de erro funcional para Storage em `backend/photo-processor/src/test/java/com/example/photoprocessor/storage/StorageResilienceTest.java`
- [ ] T077 [P] [US6] Criar testes de timeout e retry finito da publicação Pub/Sub em `backend/photo-processor/src/test/java/com/example/photoprocessor/event/PublisherResilienceTest.java`
- [ ] T078 [US6] Implementar reexecução pelo mesmo `processamentoId`, validação dos metadados existentes e reutilização do resultado em `backend/photo-processor/src/main/java/com/example/photoprocessor/processamento/PhotoProcessingService.java`
- [ ] T079 [US6] Implementar recuperação de save bem-sucedido seguido de publish falho, com novo `eventId`, mesmo `processamentoId` e sem reprocessar a original em `backend/photo-processor/src/main/java/com/example/photoprocessor/processamento/PhotoProcessingService.java`
- [ ] T080 [US6] Configurar timeouts, retries máximos, backoff exponencial e jitter planejados para Storage e Pub/Sub, sem Circuit Breaker, em `backend/photo-processor/src/main/java/com/example/photoprocessor/config/ResilienceConfig.java`
- [ ] T081 [US6] Criar teste integrado da falha save → publish → reexecução → republicação única em `backend/photo-processor/src/test/java/com/example/photoprocessor/processamento/PublishRecoveryIT.java`

### Idempotência, ordenação e retomada do photo-consumer

- [ ] T082 [P] [US6] Criar testes de evento contratualmente válido duplicado, concorrente, atrasado, fora de ordem e posterior a estado terminal, verificando ACK/no-op rastreável sem regressão em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/ConsumerIdempotencyIT.java`
- [ ] T083 [P] [US6] Criar testes de retomada de `PERSISTINDO`, reutilização/validação de dados e ausência de BLOB duplicado em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/PersistingResumeIT.java`
- [ ] T084 [US6] Implementar decisão monotônica por `processamentoId`, `sequencia_upload`, state machine e CAS/locks, sem depender da ordem do Pub/Sub, em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/EventDecisionService.java`
- [ ] T085 [US6] Implementar retomada idempotente de `PERSISTINDO` até commit completo ou `ERRO_PERSISTENCIA` registrável em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/FinalizeProcessingService.java`
- [ ] T086 [US6] Implementar ACK somente após commit ou no-op de mensagem contratualmente válida comprovadamente duplicada/atrasada/fora de ordem/terminal; manter NACK para falha transitória e mensagem malformada/contratualmente inválida em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/consumer/PhotoResultSubscriber.java`
- [ ] T087 [US6] Criar teste de múltiplas instâncias concorrentes garantindo BLOB único, promoção única e ausência de regressão em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/ConcurrentDeliveryIT.java`

### Resiliência do photo-consumer

- [ ] T088 [P] [US6] Criar testes dos deadlines/timeouts, micro-retry finito do Google Cloud Storage client, backoff/jitter e classificação de falhas transitórias em `backend/photo-consumer/src/test/java/com/example/photoconsumer/storage/StorageResilienceTest.java`
- [ ] T089 [P] [US6] Criar testes de estados OPEN, HALF_OPEN e CLOSED do Circuit Breaker seletivo do Storage em `backend/photo-consumer/src/test/java/com/example/photoconsumer/storage/StorageCircuitBreakerTest.java`
- [ ] T090 [P] [US6] Criar testes de retry transacional somente para deadlock/lock timeout e NACK em indisponibilidade do MySQL em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/DatabaseResilienceIT.java`
- [ ] T091 [US6] Configurar deadlines/timeouts e micro-retry finito com backoff/jitter no Google Cloud Storage client do consumer em `backend/photo-consumer/src/main/java/com/example/photoconsumer/config/StorageClientConfig.java`
- [ ] T092 [US6] Configurar Resilience4j somente para o Circuit Breaker seletivo do Storage, sem Retry ou TimeLimiter, em `backend/photo-consumer/pom.xml` e `backend/photo-consumer/src/main/java/com/example/photoconsumer/config/StorageCircuitBreakerConfig.java`
- [ ] T093 [US6] Configurar retry transacional MySQL finito e seguro, sem repetir erros funcionais/não transitórios, em `backend/photo-consumer/src/main/java/com/example/photoconsumer/config/DatabaseResilienceConfig.java`
- [ ] T094 [US6] Configurar em `application.yml` somente propriedades do subscriber/client do consumer, como ack/lease e flow control, e documentar seu budget finito conjunto com a macro-redelivery configurada na subscription para evitar retry storm em `backend/photo-consumer/src/main/resources/application.yml`

### Dead Letter Topic e erros definitivos

- [ ] T095 [P] [US6] Criar testes de redeliveries finitas até a DLT para payload malformado/contratualmente inválido e do envelope DLT preservando `processamentoId` quando disponível, atributos e rastreabilidade em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/consumer/DeadLetterMessageTest.java`
- [ ] T096 [US6] Criar testes Testcontainers do registro condicional de `ERRO_PERSISTENCIA`, não regressão terminal e banco totalmente indisponível em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/DeadLetterHandlerIT.java`
- [ ] T097 [US6] Implementar handler de `photo-consumer-dlq-sub` sem segunda DLT em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/consumer/DeadLetterSubscriber.java`
- [ ] T098 [US6] Registrar `ERRO_PERSISTENCIA` quando possível; com banco indisponível, não declarar persistência, manter redelivery/retenção e emitir log crítico em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/DeadLetterService.java`

### Reconciliação de processamento estagnado

- [ ] T099 [P] [US6] Criar testes de duas varreduras, janela configurável e CAS para `PROCESSANDO` estagnado em `backend/photo-api/src/test/java/com/example/photoapi/reconciliation/StalledProcessingReconcilerIT.java`
- [ ] T100 [P] [US6] Criar no `photo-api` testes da corrida de transição entre as duas varreduras do reconciliador e uma atualização concorrente, verificando CAS sem testar ACK Pub/Sub em `backend/photo-api/src/test/java/com/example/photoapi/reconciliation/ReconciliationRaceIT.java`
- [ ] T101 [P] [US6] Criar no `photo-consumer` teste de evento recebido após `ERRO_PROCESSAMENTO`, verificando ACK/no-op rastreável e ausência de ressurreição/regressão em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/service/LateEventAfterProcessingErrorIT.java`
- [ ] T102 [US6] Implementar reconciliador periódico que marca `ERRO_PROCESSAMENTO` somente após confirmação e update condicional em `backend/photo-api/src/main/java/com/example/photoapi/reconciliation/StalledProcessingReconciler.java`
- [ ] T103 [US6] Manter `PROCESSANDO` quando o objeto processado existir e emitir somente ocorrência operacional `PUBLICATION_PENDING` para recuperação, nunca novo status, em `backend/photo-api/src/main/java/com/example/photoapi/reconciliation/StalledProcessingReconciler.java`
- [ ] T104 [US6] Verificar US6 com suíte ponta a ponta autocontida no Failsafe da API, provisionando dependências via Testcontainers sem Compose pré-iniciado, para retries, timeouts, Circuit Breaker seletivo, redelivery, DLT, reexecução e estados terminais em `backend/photo-api/src/test/java/com/example/photoapi/e2e/ResilienceIT.java`

**Checkpoint**: retries/redeliveries finitos não duplicam efeitos; estados nunca regridem; falhas comunicáveis terminam corretamente e falhas incomunicáveis permanecem rastreáveis.

---

## Phase 6: User Story 3 — Listar, consultar e atualizar usuário (Priority: P2)

**Goal**: consultar usuários e alterar somente o nome, mantendo `usuarioId` imutável.

**Independent Test**: criar usuário, listar, consultar, alterar nome e verificar respostas `200` e inexistentes `404`.

- [ ] T105 [P] [US3] Criar testes HTTP de listagem e consulta `200/404` em `backend/photo-api/src/test/java/com/example/photoapi/usuario/web/UsuarioQueryControllerIT.java`
- [ ] T106 [P] [US3] Criar testes HTTP de atualização, trim/Unicode, ID imutável e `200/400/404` em `backend/photo-api/src/test/java/com/example/photoapi/usuario/web/UpdateUsuarioControllerIT.java`
- [ ] T107 [US3] Implementar listagem e consulta em `backend/photo-api/src/main/java/com/example/photoapi/usuario/service/UsuarioQueryService.java`
- [ ] T108 [US3] Implementar atualização exclusiva do nome em `backend/photo-api/src/main/java/com/example/photoapi/usuario/service/UpdateUsuarioService.java`
- [ ] T109 [US3] Expor GET de coleção/item e PUT do usuário em `backend/photo-api/src/main/java/com/example/photoapi/usuario/web/UsuarioController.java`
- [ ] T110 [US3] Verificar a US3 contra exemplos e schemas de `contracts/openapi.yaml` no teste `backend/photo-api/src/test/java/com/example/photoapi/usuario/web/UsuarioContractIT.java`

**Checkpoint**: leitura e alteração nominal funcionam sem permitir mudança do identificador.

---

## Phase 7: User Story 4 — Atualizar foto mantendo a anterior (Priority: P2)

**Goal**: aceitar novo upload com `202`, preservar a foto atual e promover somente o processamento mais novo elegível ao chegar a `PERSISTIDA`.

**Independent Test**: com foto atual existente, enviar nova foto, verificar a anterior durante o processamento e a nova após conclusão.

- [ ] T111 [P] [US4] Criar testes HTTP das mesmas validações do cadastro e resposta `202 PROCESSANDO` em `backend/photo-api/src/test/java/com/example/photoapi/foto/web/UploadPhotoControllerIT.java`
- [ ] T112 [P] [US4] Criar testes de lock na sequência, processamento ativo `409` e uploads concorrentes em `backend/photo-api/src/test/java/com/example/photoapi/foto/service/UploadPhotoConcurrencyIT.java`
- [ ] T113 [P] [US4] Criar teste ponta a ponta autocontido no Failsafe da API, com dependências via Testcontainers e sem Compose pré-iniciado, que preserva a foto anterior e promove somente a sequência elegível mais nova em `backend/photo-api/src/test/java/com/example/photoapi/e2e/PhotoReplacementIT.java`
- [ ] T114 [US4] Implementar alocação bloqueada de `sequencia_upload`, UUID e criação condicional do novo processamento em `backend/photo-api/src/main/java/com/example/photoapi/foto/service/UploadPhotoService.java`
- [ ] T115 [US4] Reutilizar validação completa e storage create-only da US1 para obter aceite consistente sem aceite parcial, respondendo `202` somente após confirmação e executando limpeza/compensação simples em falha anterior ao HTTP em `backend/photo-api/src/main/java/com/example/photoapi/foto/service/UploadPhotoService.java`
- [ ] T116 [US4] Expor `POST /api/v1/usuarios/{usuarioId}/fotos` com `202/400/404/409/413/415` em `backend/photo-api/src/main/java/com/example/photoapi/foto/web/PhotoController.java`
- [ ] T117 [US4] Reforçar no consumer a elegibilidade por `sequencia_upload` para impedir promoção de foto antiga em `backend/photo-consumer/src/main/java/com/example/photoconsumer/processamento/service/FinalizeProcessingService.java`

**Checkpoint**: atualização de foto é assíncrona e nunca remove/promove prematuramente a foto anterior.

---

## Phase 8: User Story 5 — Concorrência e exclusão retomável (Priority: P2)

**Goal**: rejeitar operações conflitantes e remover integralmente os dados com retomada idempotente após falha parcial.

**Independent Test**: provocar upload/exclusão concorrentes e falhas em cada etapa de limpeza; verificar `409`, `5xx` retomável e `204` somente após remoção integral.

- [ ] T118 [P] [US5] Criar testes de corrida que garantem um único processamento ativo e `409` aos concorrentes em `backend/photo-api/src/test/java/com/example/photoapi/foto/service/ActiveProcessingConcurrencyIT.java`
- [ ] T119 [P] [US5] Criar testes HTTP de exclusão `204/404/409/5xx` em `backend/photo-api/src/test/java/com/example/photoapi/usuario/web/DeleteUsuarioControllerIT.java`
- [ ] T120 [P] [US5] Criar testes de falha parcial em originais/processadas e retomada sem restaurar objetos removidos em `backend/photo-api/src/test/java/com/example/photoapi/usuario/service/DeleteUsuarioResumeIT.java`
- [ ] T121 [P] [US5] Criar testes de limpeza de BLOB, processamentos e usuário somente depois dos objetos em `backend/photo-api/src/test/java/com/example/photoapi/usuario/service/DeleteUsuarioOrderingIT.java`
- [ ] T122 [US5] Implementar listagem/exclusão idempotente de objetos originais e processados, tratando ausência como sucesso, em `backend/photo-api/src/main/java/com/example/photoapi/storage/UserPhotoStorageCleaner.java`
- [ ] T123 [US5] Implementar exclusão retomável com referências preservadas até a limpeza, banco por último e sem Saga/Outbox/2PC em `backend/photo-api/src/main/java/com/example/photoapi/usuario/service/DeleteUsuarioService.java`
- [ ] T124 [US5] Expor `DELETE /api/v1/usuarios/{usuarioId}` com `204` somente após remoção integral em `backend/photo-api/src/main/java/com/example/photoapi/usuario/web/UsuarioController.java`
- [ ] T125 [US5] Verificar corrida upload/exclusão e retomada após falha em teste autocontido no Failsafe da API, provisionando dependências via Testcontainers e sem Compose pré-iniciado, em `backend/photo-api/src/test/java/com/example/photoapi/e2e/DeleteResumeIT.java`

**Checkpoint**: conflitos são determinísticos e a exclusão pode ser repetida com segurança até a conclusão integral.

---

## Phase 9: Contratos, Postman, observabilidade e quality gates

**Purpose**: validar apenas aspectos transversais restantes, sem postergar testes funcionais das fases anteriores.

### Contratos e Postman

- [ ] T126 [P] Criar verificação automatizada de aderência dos endpoints implementados a `specs/001-backend-photo-processing/contracts/openapi.yaml` em `backend/photo-api/src/test/java/com/example/photoapi/contract/OpenApiContractIT.java`
- [ ] T127 [P] Criar verificação automatizada de aderência dos eventos e ausência de bytes a `specs/001-backend-photo-processing/contracts/events.md` em `backend/photo-consumer/src/test/java/com/example/photoconsumer/processamento/event/EventContractIT.java`
- [ ] T128 Criar `postman/local.postman_environment.json` com `baseUrl`, `usuarioId` e `processamentoId`
- [ ] T129 Criar happy path com captura automática dos IDs em `postman/async-photo-processing-platform.postman_collection.json`
- [ ] T130 Completar na mesma collection a matriz negativa `400/404/409/413/415` e os sucessos `201/202/200/204`, sem manipulação manual de falhas de infraestrutura

### Observabilidade e health

- [ ] T131 [P] Configurar logs JSON no `photo-api` com campos correlacionáveis e redaction em `backend/photo-api/src/main/resources/logback-spring.xml`
- [ ] T132 [P] Configurar logs JSON no `photo-consumer` com `event`, IDs, status, retry, timeout, circuit, message/delivery, DLT e erro em `backend/photo-consumer/src/main/resources/logback-spring.xml`
- [ ] T133 [P] Configurar logs JSON no `photo-processor` com os campos aplicáveis e sem bytes, conteúdo, credenciais ou segredos em `backend/photo-processor/src/main/resources/logback.xml`
- [ ] T134 [P] Criar testes de exposição restrita de `/actuator/health`, liveness e readiness no `photo-api` em `backend/photo-api/src/test/java/com/example/photoapi/actuator/HealthEndpointIT.java`
- [ ] T135 [P] Criar testes equivalentes no `photo-consumer` em `backend/photo-consumer/src/test/java/com/example/photoconsumer/actuator/HealthEndpointIT.java`
- [ ] T136 Verificar por teste executável no módulo API as dependências dos três POMs, garantindo que `photo-processor` não contém Actuator e nenhum módulo contém Prometheus/Grafana, em `backend/photo-api/src/test/java/com/example/photoapi/architecture/OperationalDependenciesTest.java`

### Testes transversais restantes e validação final

- [ ] T137 Executar a suíte transversal com MySQL 8.4 para migrations, processamento ativo sob corrida, CAS, promoção atômica e retomada de `PERSISTINDO` em `backend/`
- [ ] T138 Executar testes ponta a ponta de duplicidade, redelivery, fora de ordem, processor reexecutado, save → publish, DLT e evento tardio em `backend/`
- [ ] T139 Executar testes de imagem do processor para orientação, dimensões, proporção, ausência de upscale, JPEG e PNG em `backend/photo-processor/`
- [ ] T140 Executar `./mvnw verify` dentro de `backend/` sem `docker compose` pré-iniciado, comprovando que testes unitários, Testcontainers e Failsafe são autocontidos, e corrigir somente lacunas até JaCoCo atingir pelo menos 80% por módulo e agregado
- [ ] T141 Executar `docker compose up --build` na raiz e validar MySQL, fake-gcs-server, Pub/Sub Emulator, dispatcher, Functions Framework, API e consumer conforme `specs/001-backend-photo-processing/quickstart.md`
- [ ] T142 Validar separadamente health/readiness/liveness da API e consumer e disponibilidade/CloudEvent de teste do processor conforme `specs/001-backend-photo-processing/quickstart.md`
- [ ] T143 Executar a collection Postman completa e registrar a aprovação do happy path e da matriz HTTP em `postman/README.md`
- [ ] T144 Validar na prática retries, timeouts, Circuit Breaker seletivo, redelivery finita, DLT e exclusão retomável conforme `specs/001-backend-photo-processing/quickstart.md`
- [ ] T145 Confirmar por inspeção de `backend/`, `compose.yaml`, `docker/` e `postman/` que não há frontend, autenticação, Spring Security, JWT, OAuth2, Saga, Outbox, 2PC, runtime GCP escolhido para API/consumer ou tecnologia não aprovada

**Checkpoint**: Fase 1 validada integralmente por Maven, Testcontainers, ambiente local e Postman.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: inicia imediatamente; T006 depende de T001–T005 e T009 depende de T001–T008.
- **Phase 2 (Foundation)**: depende da Phase 1 e bloqueia todas as User Stories; Dockerfiles T013–T016 precedem o wiring T018, migrations seguem T022 → T023 → T024, e testes T029–T030 dependem delas. O wiring não implica validação funcional da função nesta fase.
- **US1 (Phase 3)**: depende da Foundation e fornece o aceite inicial usado no fluxo ponta a ponta.
- **US2 (Phase 4)**: depende da Foundation e integra o processamento criado pela US1; T061 valida dispatcher → Function somente depois do serviço T058 e da função T059, e processor/consumer convergem no E2E T074.
- **US6 (Phase 5)**: depende do caminho assíncrono básico da US2; T075–T081 endurecem o `PhotoProcessingService` existente, e a resiliência do processor e do consumer pode evoluir em paralelo antes da validação T104.
- **US3 (Phase 6)**: depende da Foundation e pode ser implementada em paralelo com US1/US2/US6.
- **US4 (Phase 7)**: depende do aceite da US1, consultas/promoção da US2 e garantias de ordem da US6.
- **US5 (Phase 8)**: depende da proteção de processamento ativo da Foundation e do storage/API construídos em US1/US4.
- **Phase 9 (Polish)**: depende das histórias selecionadas; T141–T145 exigem todas as histórias completas.

### User Story Dependencies

```text
Foundation ─┬─> US1 ─> US2 ─> US6 ─> US4 ─> US5
            └────────────────> US3
```

- **US1** entrega cadastro independente, ainda que o resultado assíncrono completo seja demonstrado na US2.
- **US2** entrega o caminho feliz processor/consumer e consultas.
- **US6** endurece esse caminho sem alterar o contrato funcional.
- **US3** é independente depois da Foundation.
- **US4** reutiliza validação/storage da US1 e promoção/ordenação de US2/US6.
- **US5** reutiliza proteção de concorrência e limpeza dos storages já existentes.

### Regras dentro de cada história

1. Criar o teste da tarefa e observar a falha esperada.
2. Implementar contratos/domínio antes do serviço e o serviço antes do endpoint/subscriber.
3. Manter downloads externos fora de transações longas.
4. Confirmar commit antes de ACK; permitir ACK/no-op apenas para mensagem contratualmente válida comprovadamente duplicada, atrasada, fora de ordem ou terminal, e NACK para mensagem malformada/contratualmente inválida.
5. Encerrar a fase somente após o checkpoint independente.

### Parallel Opportunities

- T003–T005, T007–T008 podem avançar em paralelo após T001–T002.
- T010–T016 e T020–T021 podem avançar em paralelo depois do build básico; T017–T019 e T022–T030 respeitam suas dependências locais.
- T031–T036 são paralelizáveis por módulo e arquivo.
- Em cada User Story, tarefas marcadas `[P]` são testes ou componentes em arquivos distintos.
- Após a Foundation, US3 pode avançar paralelamente ao eixo US1 → US2 → US6.
- Dentro da US6, resiliência do processor, consumer e preparação dos testes DLT/reconciliador podem avançar em paralelo respeitando suas dependências locais.

---

## Parallel Example: User Story 6

```text
T075 Processor idempotency tests
T076 Processor Storage resilience tests
T077 Processor Pub/Sub resilience tests

T082 Consumer ordering/idempotency tests
T083 PERSISTINDO resume tests

T088 Consumer Storage deadline/micro-retry tests
T089 Selective Circuit Breaker tests
T090 MySQL resilience tests
```

Esses grupos mexem em arquivos distintos. A implementação correspondente começa depois que os testes do grupo falharem pela razão esperada.

---

## Implementation Strategy

### Primeiro incremento executável

1. Concluir Phase 1 e Phase 2.
2. Concluir US1 para validar o aceite HTTP e armazenamento original.
3. Concluir US2 para obter o primeiro fluxo assíncrono ponta a ponta.
4. Validar os checkpoints antes de adicionar resiliência e histórias P2.

### Entrega incremental

1. **Fundação**: build, ambiente local, schema e contratos internos.
2. **US1 + US2**: cadastro e caminho feliz completo.
3. **US6**: idempotência e modos de falha do caminho assíncrono.
4. **US3**: manutenção cadastral.
5. **US4**: substituição segura da foto.
6. **US5**: concorrência e exclusão retomável.
7. **Validação final**: contratos, Postman, health, cobertura e quickstart.

---

## Guardrails permanentes

- Somente backend nesta fase; nenhum frontend, Vite, HTML de aplicação ou TypeScript de interface.
- Somente sete estados funcionais; `PUBLICATION_PENDING` é ocorrência operacional/log e o processamento permanece `PROCESSANDO`.
- O sistema não depende da ordem do Pub/Sub; `processamentoId`, `sequencia_upload`, state machine e CAS/locks protegem monotonicidade.
- Micro-retry do client e macro-retry/redelivery do Pub/Sub têm budgets finitos, dimensionados conjuntamente e sem retry storm.
- Usar a terminologia **Dead Letter Topic (DLT)** do Google Cloud Pub/Sub.
- Actuator somente em `photo-api` e `photo-consumer`; sem Prometheus, Grafana ou plataforma adicional.
- `photo-processor` permanece Cloud Run Function/Functions Framework, sem Spring Boot, Actuator, Circuit Breaker ou MySQL.
- O runtime GCP de `photo-api` e `photo-consumer` permanece para a etapa de deployment.
- Não introduzir autenticação, Spring Security, JWT, OAuth2, Saga, Outbox, 2PC ou abstrações sem necessidade aprovada.
