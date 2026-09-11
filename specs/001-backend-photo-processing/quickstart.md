# Quickstart: Validação local da Fase 1

Este guia descreve a validação esperada após implementação. Não contém código de aplicação.

## Prerequisites

- JDK 25
- Docker Engine com Docker Compose
- portas locais livres para API, MySQL, fake-gcs-server, Pub/Sub Emulator e Functions Framework
- Postman ou Postman CLI/Newman compatível com a collection

O build sempre usa `backend/mvnw`; Maven global não é requisito.

## 1. Build and automated gates

A partir da raiz do repositório, entre em `backend/`. O Maven Wrapper deve ser executado dentro
desse diretório:

```bash
cd backend
./mvnw verify
cd ..
```

Esperado:

- módulos `photo-api`, `photo-consumer` e `photo-processor` aprovados;
- testes unitários e integração MySQL 8.4 aprovados;
- JaCoCo com pelo menos 60% de linhas por módulo e agregado;
- testes de contrato de imagem, eventos, idempotência e resiliência aprovados.

## 2. Start local dependencies and backend

De volta à raiz do repositório, onde está `compose.yaml`, execute:

```bash
docker compose up --build
```

O bootstrap deve criar:

- schema via Flyway;
- buckets `fotos-usuarios-original` e `fotos-usuarios-processadas`;
- tópico `foto-processada`, subscription `photo-consumer-sub`;
- tópico `foto-processada-dlq`, subscription `photo-consumer-dlq-sub`.

Valide separadamente, sem transformar este guia em referência de configuração do Actuator:

- `photo-api`: consultar `/actuator/health`; consultar `/actuator/health/liveness` e
  `/actuator/health/readiness` quando configurados.
- `photo-consumer`: consultar `/actuator/health`; consultar `/actuator/health/liveness` e
  `/actuator/health/readiness` quando configurados.
- `photo-processor`: não possui Actuator. Verificar somente a disponibilidade do Functions
  Framework e o recebimento de um CloudEvent de teste entregue pelo dispatcher local conforme
  [events.md](contracts/events.md).

A disponibilidade do Functions Framework não equivale aos checks de health/readiness dos serviços
Spring Boot. Endpoints Actuator além dos três checks de health necessários não devem estar expostos.

## 3. Configure Postman

Importe:

```text
postman/async-photo-processing-platform.postman_collection.json
postman/local.postman_environment.json
```

Variáveis mínimas:

| Variable | Initial value |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `usuarioId` | vazio; capturado de `response.id` no cadastro (`usuarioId = response.id`) |
| `processamentoId` | vazio; capturado em cada upload |

Nenhum segredo real deve existir no environment.

## 4. Happy path

1. Cadastrar com nome e JPG/PNG válido; esperar 201, atribuir `usuarioId = response.id` e capturar
   `processamentoId`.
2. Consultar processamento; esperar 200 e estado observável.
3. Consultar até `PERSISTIDA`, com limite de polling para não criar loop infinito.
4. Consultar foto; esperar HTTP 200, `Content-Type` `image/jpeg` ou `image/png` e corpo não vazio.
5. Listar/consultar usuário; esperar 200.
6. Atualizar nome; esperar 200, novo nome e mesmo `usuarioId`.
7. Enviar nova foto; esperar 202 e novo `processamentoId`.
8. Durante processamento, confirmar que a foto anterior ainda é retornada.
9. Após `PERSISTIDA`, confirmar promoção da nova foto.
10. Excluir sem ativo; esperar 204 e depois 404 para usuário/foto.

## 5. HTTP negative matrix

| Scenario | Expected |
|---|---:|
| nome inválido | 400 |
| foto obrigatória ausente | 400 |
| múltiplas imagens | 400 |
| arquivo >10.485.760 bytes | 413 |
| exatamente 10.485.760 bytes em formato válido | aceito |
| conteúdo fora de JPG/JPEG/PNG | 415 |
| qualquer operação sobre usuário inexistente | 404 |
| processamento inexistente | 404 |
| foto em processamento, sem anterior | 409 |
| foto ausente após erro terminal | 404 |
| segundo upload durante ativo | 409 |
| exclusão durante ativo | 409 |

## 6. Async and resilience validation

- Publicar o mesmo resultado três vezes: uma persistência/promoção, demais ACK/no-op.
- Entregar resultado antigo depois de foto mais nova: nenhum estado/pointer regride.
- Falhar publish depois do save, reexecutar mesmo `processamentoId`: HEAD encontra resultado,
  transformação não roda e publish é retomado.
- Simular timeout/erro transitório por integração: número finito, backoff/jitter observáveis e nenhum
  efeito duplicado.
- Simular erro funcional/4xx: nenhuma tentativa automática.
- Abrir Circuit Breaker de Storage em API/consumer: chamadas falham rápido; 4xx não contam; verificar
  half-open/close. Confirmar ausência de Circuit Breaker no processor.
- Esgotar consumer: mensagem chega à DLT com `processamentoId`; se banco disponível, estado torna-se
  `ERRO_PERSISTENCIA`.
- Manter banco indisponível na DLT: mensagem permanece para redelivery/retenção e log crítico é
  emitido; após retorno do banco, estado é registrado.
- Criar `PROCESSANDO` estagnado sem objeto processado: duas varreduras após 15 minutos confirmam e
  registram `ERRO_PROCESSAMENTO`. Com objeto processado presente, mantém ativo e sinaliza
  uma ocorrência operacional/log de recuperação de republicação pendente; isso não cria novo
  status de `PROCESSAMENTO_FOTO`, que permanece `PROCESSANDO`.

Dimensões <=1024, correção de orientação EXIF, ausência de upscale e preservação do comportamento
de PNG são validadas nos testes automatizados do `photo-processor`, não no Happy Path do Postman.

## 7. Deletion recovery

1. Criar usuário com ao menos duas fotos históricas terminadas.
2. Induzir falha após remover parte dos objetos.
3. Confirmar resposta 5xx, referências ainda disponíveis e nenhum 204 falso.
4. Restaurar dependência e repetir DELETE.
5. Confirmar que objetos já ausentes são no-op e restantes são removidos.
6. Confirmar 204 somente após remoção de usuário, processamentos, BLOB e todos os objetos.

## 8. Structured log audit

Filtrar por um `processamentoId` e confirmar uma trilha completa: upload, transições, Storage,
processor, publish, delivery, persistência e promoção. Nos cenários aplicáveis, confirmar tentativa,
timeout, estado do circuito, message ID/delivery attempt, DLT e categoria de erro. Logs não devem
conter bytes, credenciais ou conteúdo da imagem.

## 9. GCP contract smoke test

Antes de declarar paridade GCP:

- implantar function com runtime Java 25 e trigger finalize apenas no bucket original;
- confirmar payload CloudEvent/generation contra fixture local;
- publicar/consumir uma referência real sem bytes;
- validar permissões de publisher, subscriber e encaminhamento DLT;
- executar um caso duplicado e um caso de publish recuperado.

O smoke GCP complementa, não substitui, o fluxo local obrigatório.
