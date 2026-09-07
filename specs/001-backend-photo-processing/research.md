# Phase 0 Research: Backend de Processamento Assíncrono de Fotos

Todas as decisões abaixo resolvem o Technical Context. Versões devem ser fixadas no build e
validadas pela toolchain Java 25 antes da implementação funcional.

## Processamento de imagem

**Decision**: Thumbnailator 0.4.21 como pipeline, com TwelveMonkeys ImageIO JPEG como codec
complementar. Usar orientação EXIF, bounding box 1024x1024, proporção preservada, sem upscale;
JPEG com qualidade inicial 0,85 e PNG lossless preservando alpha.

**Rationale**: É Java puro, não exige binários nativos e cobre diretamente orientação e resize. O
plugin TwelveMonkeys amplia robustez de leitura JPEG e possui correções relevantes ao JDK 25.

**Alternatives considered**: Java2D/ImageIO puro exigiria implementar EXIF/resampling/encode;
metadata-extractor ainda deixaria transformação manual; ImageMagick/IM4Java aumentaria imagem,
cold start e operação nativa.

**Proteção contra image bombs**: O `photo-processor` adotará a propriedade configurável
`photo.processing.max-pixels`, com padrão de `25_000_000` pixels. Sempre que for tecnicamente
possível obter largura e altura previamente, o limite será verificado antes da decodificação completa
e transformação, calculando `largura × altura` de forma segura contra overflow. Imagens exatamente
no limite são aceitas; acima dele constituem erro funcional definitivo de processamento e seguem o
fluxo existente de `PhotoProcessingError` / `ERRO_PROCESSAMENTO`. A proteção é independente do
limite HTTP de 10 MiB e não requer biblioteca adicional.

**Sources**: [Thumbnailator](https://github.com/coobird/thumbnailator),
[Builder API](https://coobird.github.io/thumbnailator/javadoc/0.4.19/net/coobird/thumbnailator/Thumbnails.Builder.html),
[TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys),
[Java runtime](https://docs.cloud.google.com/run/docs/runtimes/java).

## Função e contrato de evento

**Decision**: Functions Framework Java com `CloudEventFunction`, consumindo o evento
`google.cloud.storage.object.v1.finalized`. Em GCP, Eventarc filtra o bucket original. Localmente,
um dispatcher de infraestrutura converte finalização do fake-gcs-server no mesmo CloudEvent.

**Rationale**: Um único handler mantém paridade local/GCP e preserva o processor como função leve,
stateless e sem MySQL. O dispatcher evita que a resposta HTTP espere processamento.

**Alternatives considered**: Acionamento direto pela API cria acoplamento e janela de perda;
depender de notificações experimentais do fake-gcs-server sem smoke test é frágil; polling é
fallback do dispatcher, com watermark e dedupe.

**Sources**: [Functions Framework Java](https://github.com/GoogleCloudPlatform/functions-framework-java),
[Function triggers](https://docs.cloud.google.com/run/docs/function-triggers),
[Eventarc retry](https://docs.cloud.google.com/eventarc/docs/retry-events),
[fake-gcs-server](https://github.com/fsouza/fake-gcs-server).

## Idempotência do processor

**Decision**: Chave processada determinística por `usuarioId/processamentoId`, HEAD antes de
transformar e criação com `generationMatch=0`. Objeto existente com metadados coincidentes é
reutilizado e somente o publish é repetido.

**Rationale**: Fecha a janela não transacional Storage -> Pub/Sub. Confirmação perdida pode duplicar
publish, mas nunca transformação/objeto; o consumer absorve duplicatas.

**Alternatives considered**: Outbox exige banco proibido à função; Saga/2PC não se aplica aos
serviços; sobrescrita incondicional gera reprocessamento e corrida.

**Sources**: [Cloud Storage retry strategy](https://cloud.google.com/storage/docs/retry-strategy),
[Pub/Sub publisher](https://cloud.google.com/java/docs/reference/google-cloud-pubsub/latest/com.google.cloud.pubsub.v1.Publisher).

## Resilience4j

**Decision**: Fixar `resilience4j-spring-boot4:2.4.0` explicitamente, apenas nos serviços Spring
long-lived e somente para Circuit Breaker de chamadas síncronas repetidas a Storage.
Timeouts ficam nos clientes; retries Google ficam nos próprios clients. Build e testes Java 25 são
gate porque não existe matriz formal de compatibilidade publicada.

**Rationale**: A versão 2.4.0 declara Spring Boot 4. Circuito local à função teria estado efêmero e
fragmentado; no consumer, redelivery é preferível a retry aninhado do banco.

**Alternatives considered**: Rejeitar Resilience4j perderia fail-fast nos serviços long-lived;
aplicá-lo em todas as integrações violaria simplicidade e inflaria contagem de falhas/retries.

**Sources**: [Resilience4j 2.4.0](https://github.com/resilience4j/resilience4j/releases/tag/v2.4.0),
[Spring Boot configuration](https://resilience4j.readme.io/docs/getting-started-3),
[BOM caveat](https://github.com/resilience4j/resilience4j/issues/2427).

## Retry, timeout e anti-storm

**Decision**: Aplicar budgets da tabela em `plan.md`; retry só para falha transitória allowlisted,
com exponential backoff e jitter. Micro-retry do client atua dentro de uma operação e macro-retry do
Pub/Sub ocorre por redelivery entre execuções; podem coexistir sob budget conjunto e finito. I/O
bloqueante usa deadline do client, não `TimeLimiter`.

**Rationale**: Tentativas finitas e jitter recuperam falhas breves; dimensionamento conjunto limita
a multiplicação micro-retry x redelivery. Precondições tornam writes repetíveis com segurança.

**Alternatives considered**: Retry genérico de exceção pode repetir validação/autorização; annotations
empilhadas obscurecem a ordem Retry/Circuit Breaker; timeout externo não interrompe I/O bloqueante.

**Sources**: [Cloud Storage retry](https://cloud.google.com/storage/docs/retry-strategy),
[Pub/Sub errors](https://cloud.google.com/pubsub/docs/reference/error-codes),
[Cloud SQL connections](https://cloud.google.com/sql/docs/mysql/manage-connections).

## Pub/Sub e Dead Letter Topic

**Decision**: At-least-once com tópico `foto-processada`, subscription `photo-consumer-sub`, retry
10–300s, ack deadline inicial 60s e 8 tentativas best-effort; DLT `foto-processada-dlq`, subscription
`photo-consumer-dlq-sub`, retenção 7 dias. ACK só após commit ou no-op de mensagem contratualmente
válida que seja duplicada, atrasada, fora de ordem, terminal ou logicamente não aplicável. Payload
não desserializável, `schemaVersion` incompatível, IDs obrigatórios ausentes ou referência
estruturalmente inválida falha/NACK e segue redelivery finita até a DLT.

**Rationale**: Exactly-once não elimina duplicata do publisher e adiciona restrições/latência. A
idempotência por `processamentoId` continua necessária. DLT retém diagnóstico e recuperação.

**Alternatives considered**: Exactly-once não resolve toda a cadeia; segunda DLQ pode criar loop;
retry imediato do subscriber causa storm durante indisponibilidade do banco.

**Sources**: [Retry policy](https://cloud.google.com/pubsub/docs/subscription-retry-policy),
[Dead-letter topics](https://cloud.google.com/pubsub/docs/dead-letter-topics),
[Subscribe best practices](https://cloud.google.com/pubsub/docs/subscribe-best-practices),
[Exactly-once](https://cloud.google.com/pubsub/docs/exactly-once-delivery),
[Emulator](https://cloud.google.com/pubsub/docs/emulator).

## Concorrência e ordem

**Decision**: Sequência monotônica de upload por usuário, único ativo por índice MySQL, locks por
usuário/processamento e transições condicionais. Promoção exige sequência candidata maior que a
atual e ocorre no mesmo commit de `PERSISTIDA`.

**Rationale**: A regra permanece válida sob duas instâncias, duplicatas e mensagem atrasada sem
depender de ordem do broker.

**Alternatives considered**: Check apenas na aplicação sofre race; UUID/data como ordenação têm
empates/semântica fraca; ordering key não substitui controle transacional.

## Retomada de persistência final

**Decision**: Somente um `PhotoProcessingResult` equivalente do mesmo `processamentoId` em
`PERSISTINDO` retoma o trabalho. Dados já presentes são validados/reutilizados, o BLOB não é
duplicado, e metadados, promoção e `PERSISTIDA` concluem transacionalmente. Um
`PhotoProcessingError` recebido em `PERSISTINDO` é ACK/no-op rastreável e não regride o estado para
`ERRO_PROCESSAMENTO`. Falha definitiva registrável leva a `ERRO_PERSISTENCIA`. ACK só ocorre após
commit ou no-op contratualmente válido; mensagem malformada ou contratualmente inválida falha/NACK
até a DLT.

**Rationale**: `PERSISTINDO` é ativo e pode representar entrega interrompida depois de parte do
trabalho; tratá-lo como fora de ordem deixaria o usuário bloqueado permanentemente.

**Alternatives considered**: Reiniciar cegamente pode duplicar BLOB; ACK imediato perde trabalho;
novo estado não é permitido pela especificação.

## Exclusão retomável

**Decision**: Remover objetos por chaves conhecidas, tratar 404 como sucesso, e só então apagar
registros em transação. Retornar 204 somente após verificação integral; falha retorna 5xx e a mesma
requisição pode retomar.

**Rationale**: Delete de objeto é naturalmente repetível. Manter referências até limpeza concluir
permite retomada sem Saga/2PC nem restauração de objetos já removidos.

**Alternatives considered**: Apagar banco primeiro perde referências; rollback de object storage é
impossível/contraproducente; exclusão assíncrona violaria o 204 somente após remoção integral.

## Runtime GCP dos serviços Spring

**Decision**: Adiar a escolha do runtime GCP de `photo-api` e `photo-consumer` para a etapa de
deployment, após validação local completa. Cloud Run, GKE e Compute Engine permanecem alternativas,
não decisões deste plano. Cloud Run Function para o processor, Cloud Storage, Pub/Sub e Cloud SQL
continuam definidos.

**Rationale**: A feature exige primeiro comprovar o fluxo local e não fornece requisitos de escala,
operação ou rede suficientes para escolher responsavelmente o runtime dos serviços long-lived.

**Alternatives considered**: Selecionar agora qualquer runtime apenas preencheria o plano e poderia
antecipar complexidade operacional sem requisito concreto.

## Health, liveness e readiness

**Decision**: Adotar Spring Boot Actuator no `photo-api` e no `photo-consumer`, com exposição
restrita a `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness` quando
aplicável. O `photo-processor` não usa Actuator porque permanece Cloud Run Function executada pelo
Functions Framework.

**Rationale**: Serviços de longa duração precisam indicar saúde, capacidade de receber tráfego e
vivacidade no fluxo local e em futura implantação gerenciada. A função tem ciclo de execução e
mecanismo operacional diferentes, portanto Actuator não agrega valor.

**Alternatives considered**: Checks ad hoc duplicariam uma capacidade padrão do Spring Boot;
expor todos os endpoints Actuator ampliaria superfície operacional sem necessidade. Prometheus e
Grafana foram rejeitados na Fase 1 porque health estruturado e logs existentes atendem ao MVP.

Actuator é infraestrutura operacional, não parte do contrato funcional da aplicação; seus endpoints
não entram no OpenAPI. Endpoints administrativos desnecessários permanecem desabilitados ou não
expostos.
