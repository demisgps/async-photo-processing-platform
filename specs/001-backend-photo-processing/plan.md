# Implementation Plan: Backend de Processamento Assíncrono de Fotos — Fase 1

**Branch**: `001-backend-photo-processing` | **Date**: 2026-09-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-backend-photo-processing/spec.md`

**Note**: Este documento encerra o planejamento Phase 0/Phase 1. Não cria `tasks.md`, código ou
frontend.

## Summary

Construir três componentes backend: `photo-api` recebe o CRUD e uploads, registra usuário e
processamento no MySQL e preserva a original; `photo-processor`, uma Cloud Run Function Java sem
Spring e sem banco, transforma a imagem de modo idempotente e publica referência/metadados; e
`photo-consumer` consome em regime at-least-once, persiste a processada e promove a foto atual em
uma transação idempotente. O ambiente local reproduz o fluxo por Docker Compose, fake-gcs-server,
um adaptador de eventos local, Functions Framework e Pub/Sub Emulator.

O desenho usa chaves de objeto determinísticas, controle transacional/índices no MySQL, transições
condicionais e sequência monotônica por usuário. Retry, timeout, Circuit Breaker e DLQ são aplicados
somente aos modos de falha documentados, com budgets conjuntos e finitos por integração.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.0.0 e Spring Boot Actuator (`photo-api`, `photo-consumer`),
Spring Data JPA, Validation, Flyway, Google Cloud Storage/Pub/Sub Java clients, Functions Framework Java
(`photo-processor`), Thumbnailator 0.4.21, TwelveMonkeys ImageIO JPEG, Resilience4j Spring Boot 4
2.4.0 apenas nos serviços long-lived onde indicado

**Storage**: MySQL 8.4 LTS local e Cloud SQL for MySQL no GCP; fake-gcs-server local e Cloud Storage
no GCP; buckets `fotos-usuarios-original` e `fotos-usuarios-processadas`

**Testing**: JUnit 5, Mockito, Testcontainers (MySQL 8.4 e integrações em contêiner), testes de
contrato CloudEvent/Pub/Sub, JaCoCo com mínimo de 80% de linhas e Postman

**Target Platform**: Linux containers localmente; Cloud Run Functions Java 25 para o processor;
Cloud SQL, Cloud Storage e Pub/Sub gerenciados. O runtime GCP definitivo de `photo-api` e
`photo-consumer` será decidido na etapa de deployment após a validação local completa; este plano
não antecipa escolha entre Cloud Run, GKE ou Compute Engine.

**Project Type**: Backend distribuído com dois microserviços Spring Boot e uma função stateless

**Performance Goals**: Upload HTTP não espera transformação; aceita no máximo 10 MiB; processamento
mantém pico de memória limitado por validação de dimensões/pixels; subscriber usa flow control para
não exceder pool de conexões. Metas de latência/carga além desses limites não foram definidas pela
feature e serão medidas antes de qualquer otimização.

**Constraints**: Backend only; Maven 3.9.12 Wrapper; imagens até 10.485.760 bytes; saída máxima
1024x1024 sem upscale; Pub/Sub sem bytes; processor sem MySQL; at-least-once; um processamento ativo
por usuário; 80% de cobertura; sem Saga, Outbox ou 2PC

**Scale/Scope**: MVP de uma API, um consumidor e uma função, dois buckets, um tópico principal, uma
subscription principal e um Dead Letter Topic. Sem frontend, autenticação ou plataforma adicional
de observabilidade.

## Constitution Check

*GATE: aprovado antes da pesquisa e reavaliado após o desenho Phase 1.*

| Gate constitucional | Evidência no plano | Estado pré/pós-design |
|---|---|---|
| Backend First | Somente `backend/`, infraestrutura local, contratos e Postman | PASS / PASS |
| Stack padronizada | Java 25, Boot 4.0.0, Wrapper 3.9.12, MySQL 8.4, Flyway | PASS / PASS |
| Package by Feature e camadas | Árvores por `usuario`, `foto`, `processamento`; Controller -> Service -> Repository | PASS / PASS |
| Qualidade | JUnit/Mockito/Testcontainers/JaCoCo 80% e Postman planejados | PASS / PASS |
| Fronteiras assíncronas | Processor é Function sem MySQL; eventos só com referências/metadados | PASS / PASS |
| Idempotência/reexecução | Chaves determinísticas, precondição de criação, locks/CAS e sequência de upload | PASS / PASS |
| Resiliência pragmática | Cada mecanismo mapeado a falha; tentativas limitadas e anti-storm | PASS / PASS |
| DLQ e rastreabilidade | DLT/subscription, `processamentoId`, tratamento com banco indisponível | PASS / PASS |
| Local first/GCP target | Compose completo e mapeamento equivalente ao GCP | PASS / PASS |
| Simplicidade | Sem frontend, Security, Saga, Outbox, 2PC ou plataforma nova | PASS / PASS |

Nenhuma violação constitucional exige justificativa. Todas as dúvidas de pesquisa foram resolvidas
em [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/001-backend-photo-processing/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── openapi.yaml
│   └── events.md
└── checklists/
    └── requirements.md
```

`tasks.md` não é criado nesta etapa.

### Source Code (repository root)

```text
backend/
├── pom.xml                         # agregador Maven, sem código de domínio
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/
├── photo-api/
│   └── src/
│       ├── main/java/.../
│       │   ├── usuario/{controller,service,domain,repository,request,response}/
│       │   ├── foto/{controller,service,domain,repository,request,response}/
│       │   ├── processamento/{service,domain,repository,response}/
│       │   ├── storage/
│       │   ├── reconciliation/
│       │   ├── config/
│       │   └── exception/
│       └── test/java/.../          # espelha features; unit/integration por sufixo
├── photo-consumer/
│   └── src/
│       ├── main/java/.../
│       │   ├── processamento/{consumer,service,domain,repository,event}/
│       │   ├── storage/
│       │   ├── config/
│       │   └── exception/
│       └── test/java/.../
└── photo-processor/
    └── src/
        ├── main/java/.../
        │   ├── processamento/
        │   ├── imagem/
        │   ├── storage/
        │   ├── event/
        │   └── config/
        └── test/java/.../
docker/
├── mysql/
├── pubsub/
└── storage-event-dispatcher/       # adaptador infra local; não domínio/microserviço de produto
postman/
├── async-photo-processing-platform.postman_collection.json
└── local.postman_environment.json
docs/
compose.yaml
```

**Structure Decision**: Monorepo backend com um agregador Maven e três módulos implantáveis. A
função usa Functions Framework, não Spring Boot. O dispatcher existe somente no perfil local para
traduzir finalização do fake storage no mesmo CloudEvent consumido em produção; não contém regra de
negócio. Não há diretório frontend.

## Component Design

### photo-api

- Expõe exatamente o contrato em `contracts/openapi.yaml`.
- Inclui Spring Boot Actuator para expor somente `/actuator/health`,
  `/actuator/health/liveness` e `/actuator/health/readiness` quando aplicável. Esses endpoints são
  operacionais, não integram o contrato funcional OpenAPI, e endpoints administrativos adicionais
  não são expostos na Fase 1.
- Valida nome, cardinalidade, magic bytes/decodificação e limite antes de criar dados permanentes.
- Sob lock do usuário, aloca `sequencia_upload`, cria o processamento e usa a restrição exclusiva
  de processamento ativo; grava a original com chave determinística e precondição create-only.
- Cadastro só retorna 201 e upload posterior 202 após original confirmada e estado `PROCESSANDO`.
- Usa compensação simples apenas antes do aceite HTTP: se o primeiro cadastro falhar antes da
  original confirmada, remove o registro incompleto/objeto se existir. Isso não é Saga.
- Executa exclusão retomável: lista referências conhecidas, trata objeto ausente como removido,
  apaga objetos, imagem persistida, processamentos e usuário; retorna 204 apenas após verificação
  integral. Falha parcial retorna 5xx e a repetição retoma idempotentemente.
- Um reconciliador periódico detecta `PROCESSANDO` acima de 15 minutos (configurável e maior que o
  orçamento da função). Se não houver processada determinística, após confirmação em duas varreduras
  separadas marca `ERRO_PROCESSAMENTO` por atualização condicional. Se houver processada, emite uma
  ocorrência operacional estruturada `PUBLICATION_PENDING` e mantém o estado `PROCESSANDO` para
  reexecução operacional; `PUBLICATION_PENDING` nunca é persistido como estado e a API nunca publica
  em nome do processor.
- Se o reconciliador vencer a transição condicional para `ERRO_PROCESSAMENTO`, qualquer resultado
  tardio do mesmo `processamentoId` é ACK/no-op rastreável. Estado terminal nunca é ressuscitado.

### photo-processor

- Implementa `CloudEventFunction` e recebe evento de finalização do bucket original.
- Valida bucket, chave, geração, `usuarioId` e `processamentoId`; ignora origem processada.
- Faz HEAD na chave processada determinística. Se objeto/metadados coincidirem, pula download e
  transformação e publica novamente o mesmo resultado lógico.
- Se ausente, baixa a original, valida formato/dimensões, corrige EXIF, reduz dentro de 1024x1024
  sem upscale e reencoda. Cria destino com `generationMatch=0`; corrida/412 volta ao HEAD.
- Aguarda confirmação do publish. Falha transitória após salvar lança erro para reexecução da
  plataforma. Duplicata de publicação é aceita e neutralizada no consumer.
- Erro definitivo de imagem publica evento `ERRO_PROCESSAMENTO`; se a publicação estiver totalmente
  indisponível, preserva logs correlacionados e deixa a reconciliação posterior concluir quando
  detectável. Não acessa MySQL, não usa Circuit Breaker e não inclui Spring Boot Actuator; sua
  disponibilidade é verificada pelo Functions Framework e por entrega de CloudEvent de teste.

### photo-consumer

- Inclui Spring Boot Actuator com a mesma exposição restrita de health, liveness e readiness do
  `photo-api`, permitindo checks locais e futura integração com containers/orquestração.
- StreamingPull com flow control alinhado ao pool MySQL; ACK somente após commit ou no-op terminal.
- Valida schema e referências. Mensagem inválida é redeliverada até DLT para diagnóstico.
- Trava `PROCESSAMENTO_FOTO` e `USUARIO`; transições usam estado esperado e nunca regridem.
- `PROCESSADA -> PERSISTINDO` é condicional. Download ocorre fora da transação longa. Redelivery
  válida do mesmo `processamentoId` encontrada em `PERSISTINDO` retoma a persistência final; não é
  fora de ordem nem no-op. A retomada valida/reutiliza BLOB e metadados existentes quando aplicável,
  sem duplicá-los, e conclui metadados, promoção e `PERSISTIDA` no mesmo commit. Falha definitiva
  registrável termina em `ERRO_PERSISTENCIA`.
- Duplicata terminal equivalente vira ACK/no-op; mensagem antiga/fora de ordem vira ACK/no-op com
  log. ACK ocorre somente após commit, terminal no-op ou mensagem definitivamente inválida tratada.
  Falha transitória do banco causa NACK. Esgotamento vai ao DLT; handler tenta marcar
  `ERRO_PERSISTENCIA` sem regredir terminal. Com banco totalmente indisponível, NACK na subscription
  do DLT mantém redelivery/retenção e gera log crítico; não há segunda DLQ.

## Persistence and Concurrency Strategy

- O modelo detalhado está em [data-model.md](data-model.md).
- `USUARIO.id` é `BIGINT AUTO_INCREMENT`; `PROCESSAMENTO_FOTO.id` é UUID binário e inclui
  `sequencia_upload` monotônica alocada sob lock do usuário.
- Coluna gerada `usuario_ativo_id` vale `usuario_id` apenas para estados ativos e `NULL` nos
  terminais; índice unique aproveita múltiplos `NULL` do MySQL e torna a regra concorrente
  invariável no banco.
- `version` e atualizações `WHERE status = :expected` implementam compare-and-set. Locks
  `SELECT ... FOR UPDATE` serializam upload, consumo/promoção e exclusão do mesmo usuário.
- A FK de `foto_atual_processamento_id` garante apenas que o processamento referenciado existe.
  Pertencimento ao mesmo usuário, elegibilidade e estado são garantidos na promoção por locks, CAS,
  `sequencia_upload` e testes. A transação final une BLOB, metadados, `PERSISTIDA` e ponteiro; falha
  de promoção mantém tudo fora de `PERSISTIDA` e pode ser repetida.
- Flyway cria esquema, checks, FKs e índices. Migrações são append-only e testadas no MySQL 8.4.

## Object Storage and Event Flow

Chaves:

```text
fotos-usuarios-original/{usuarioId}/{processamentoId}/arquivo.<ext>
fotos-usuarios-processadas/{usuarioId}/{processamentoId}/arquivo.<ext>
```

Metadados incluem `usuarioId`, `processamentoId`, bucket/chave/generation da origem, checksum,
content type e versão do algoritmo. A original nunca é sobrescrita. A processada usa create-only;
um resultado existente só é reutilizado se os metadados corresponderem, evitando confundir versões.

Local:

```text
photo-api -> fake-gcs-server/original
          -> storage-event-dispatcher (assíncrono, dedupe bucket+objeto+generation)
          -> Functions Framework/photo-processor
          -> fake-gcs-server/processadas
          -> Pub/Sub Emulator
          -> photo-consumer
          -> MySQL 8.4
```

GCP:

```text
Cloud Storage/original -> Eventarc finalize -> Cloud Run Function/photo-processor
Cloud Run Function -> Cloud Storage/processadas -> Pub/Sub/foto-processada
Pub/Sub/photo-consumer-sub -> photo-consumer -> Cloud SQL for MySQL
```

## Resilience Design

| Integration | Failure modes | Timeout | Retry/backoff | Non-retry | Circuit Breaker |
|---|---|---|---|---|---|
| API -> Storage | reset, deadline, 408/429/5xx | connect 2s; RPC 10s; total 20s | 3 total, exponential 200ms x2, cap 2s, jitter | validation, 400/401/403/404/409/412/413/415 | Yes: count 20, min 10, 50% failures, open 15s, half-open 3 |
| Processor -> Storage | unavailable/deadline/429/5xx | RPC 10s; total 30s | 4 total, 500ms x2, cap 4s, jitter | invalid image/auth/permission/not-found origin; 412 takes idempotent HEAD path | No |
| Processor -> Pub/Sub | UNAVAILABLE, DEADLINE_EXCEEDED, ABORTED, transient RESOURCE_EXHAUSTED | RPC 5s; total 20s | 4 total, 250ms x2, cap 4s, jitter | INVALID_ARGUMENT, auth, permission, NOT_FOUND, structural quota/config | No |
| Consumer -> Storage | unavailable/deadline/429/5xx | RPC 10s; total 20s | 3 total, 200ms x2, cap 2s, jitter | invalid ref/auth/permission/not found | Yes, same GCS profile |
| Consumer -> MySQL | deadlock/lock timeout | pool wait 3s; validation 1s; statement 10s | transaction inteira, 2 total, 100ms + jitter | constraint/domain/syntax/auth; outage causes NACK | No |
| Pub/Sub delivery | NACK, subscriber outage | ack deadline 60s com lease | broker: min 10s/max 300s; 8 attempts best-effort | ACK após commit ou no-op terminal/inválido | N/A |

Resilience4j 2.4.0 será usado explicitamente no `photo-api` e no `photo-consumer` para os circuitos
de chamadas síncronas a Storage; sua compatibilidade Spring Boot 4 é declarada upstream, mas o
build/test Java 25 é gate por não haver matriz formal publicada. Deadlines permanecem configurados
nos clientes bloqueantes; `TimeLimiter` não será usado como substituto de timeout de I/O.

Micro-retry do client recupera falhas transitórias curtas dentro de uma entrega; macro-retry do
Pub/Sub redelivera a mensagem em outra execução quando a entrega não conclui. Eles podem coexistir,
mas quantidades, deadlines e backoffs são dimensionados como budget conjunto e finito para limitar
a multiplicação de chamadas. Jitter, flow control, pools limitados e ausência de terceiro loop
genérico evitam retry storms. Erros 4xx, validações e domínio não contam no Circuit Breaker.

Pub/Sub usa tópico `foto-processada`, subscription `photo-consumer-sub`, DLT
`foto-processada-dlq` e subscription `photo-consumer-dlq-sub`. O máximo de 8 entregas é
best-effort. A DLT retém mensagens por 7 dias; falha total do banco causa NACK na subscription da
DLT e alerta por log, permitindo recuperação antes da expiração.

## Image Processing Decision

Thumbnailator 0.4.21 é o pipeline por ser Java puro, pequeno e oferecer `useExifOrientation`,
bounding box com proporção e no-upscale. TwelveMonkeys ImageIO JPEG complementa a decodificação de
JPEGs reais e correções relevantes ao JDK 25. JPEG mantém formato com qualidade inicial 0,85
(configurável e calibrada em testes); PNG preserva alpha e usa compressão lossless. Magic bytes e
decoder, não extensão/MIME isolados, determinam formato. Um limite configurável de pixels decodifica
com segurança contra imagens-bomba. ImageMagick foi rejeitado por dependência nativa/cold start;
Java2D puro por exigir orientação e pipeline manual.

## Test Strategy

- Unitários: validações, status HTTP, transições, classificador transitório, política de promoção,
  chaves/metadados, transformação EXIF/dimensões e decisões idempotentes.
- Persistência/Testcontainers MySQL 8.4: unique ativo sob corrida, locks/CAS, migrations, duplicatas,
  sequência, retomada de `PERSISTINDO` com BLOB prévio, promoção atômica, rollback, falha de promoção,
  exclusão parcial/retomada.
- Processor: orientações EXIF 1–8, JPG/PNG/alpha, limites, sem upscale, create-only concorrente,
  objeto já existente, publish falha depois do save e reexecução sem transformação.
- Assíncrono: CloudEvent duplicado, publicação duplicada, redelivery, fora de ordem, resultado tardio
  após reconciliação terminal, terminal no-op, timeout, retries finitos, circuit open/half-open nos
  serviços adotantes e anti-storm.
- Pub/Sub: tópico/subscriptions/DLT, 8 entregas aproximadas, rastreabilidade, banco indisponível e
  posterior registro de `ERRO_PERSISTENCIA`. Emulator em smoke local; contrato GCP quando disponível.
- API/Postman: todos os cenários da spec e códigos 201/202/200/204/400/404/409/413/415.
- Gate: `./mvnw verify` no agregador, JaCoCo >= 80% de linhas por módulo e agregado; cobertura não
  substitui assertions dos casos acima.

## Observability

Logs JSON em stdout com `service`, `event`, `usuarioId`, `processamentoId`, `status`,
`objectBucket`, `objectName`, `objectGeneration`, `pubsubMessageId`, `deliveryAttempt`, `retryAttempt`,
`timeoutMs`, `circuitState`, `dlqTopic`, `errorCategory` e `errorCode` quando aplicáveis. Nunca incluir
bytes, imagem, segredo ou dados sensíveis desnecessários. Eventos mínimos: upload aceito, transição,
save/HEAD/reuse, publish iniciado/confirmado/falhou, retry/timeout, circuit opened/half-open/closed,
redelivery/ACK/NACK/DLT, promoção/no-op, reconciliação e falha de exclusão. Não se adiciona plataforma
de observabilidade na Fase 1.

Spring Boot Actuator fornece health, liveness e readiness somente para `photo-api` e
`photo-consumer`. A exposição fica limitada aos três endpoints necessários; métricas e endpoints
administrativos sensíveis não são publicados. Prometheus, Grafana ou outra plataforma adicional não
são introduzidos. O Actuator é infraestrutura operacional e não altera o contrato funcional.

## Postman and Local Validation

A collection e environment seguem [quickstart.md](quickstart.md), com `baseUrl`, `usuarioId` e
`processamentoId`, sem segredos. Scripts capturam IDs, consultam até estado terminal com limite de
tentativas e verificam headers/status/content type. Cenários de falha de infraestrutura ficam em
testes automatizados/roteiro operacional, sem tornar a coleção dependente de manipulação manual.

## Complexity Tracking

Nenhuma violação constitucional. O dispatcher local e o reconciliador são necessários para,
respectivamente, reproduzir o CloudEvent sem bloquear HTTP e tornar execuções estagnadas detectáveis.
Ambos têm responsabilidade estreita; não introduzem uma nova arquitetura de domínio.
