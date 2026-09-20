# ADR-001: Runtimes serverless e Pub/Sub Push

- **Status**: Accepted
- **Data**: 2026-09-16

## Contexto

O `photo-api` é orientado a HTTP. O `photo-processor` já implementa `CloudEventsFunction`. O
Antes desta decisão, o `photo-consumer` iniciava dois subscribers StreamingPull no lifecycle Spring,
o que exigia processo e CPU continuamente ativos e conflitava com scale-to-zero de um Cloud Run
Service.

O projeto prioriza baixo custo, serviços gerenciados e simplicidade, sem GCE ou Kubernetes.

## Decisão

- Executar `photo-api` como Cloud Run Service público.
- Executar `photo-consumer` como Cloud Run Service privado.
- Migrar o transporte do consumer de StreamingPull para Pub/Sub Push autenticado.
- Usar `sa-pubsub-push` com permissão de invocação somente no consumer.
- Executar `photo-processor` como Cloud Run Function acionada por Eventarc.
- Permitir scale-to-zero para API e consumer.

Somente a borda de transporte do consumer deve mudar. Serviços de idempotência, persistência,
ordenação, retomada e DLT devem ser preservados.

## Alternativas consideradas

### GCE com StreamingPull

Evitaria a adaptação do consumer, mas criaria VM continuamente provisionada, patches, supervisão
de processo e custo ocioso.

### Cloud Run com StreamingPull e instância mínima

Manteria o código, mas exigiria CPU/lifecycle contínuos e eliminaria o principal benefício econômico
de escala a zero.

### Cloud Run Worker Pool

Compatível com worker Pull, mas adicionaria escala/operação e custo desnecessários para este volume.

### GKE

Ofereceria controle total de workloads, mas sem requisito que justifique cluster, Kubernetes ou sua
carga operacional.

## Consequências

- Consumer precisa receber e validar envelope Pub/Sub HTTP.
- O protocolo Push aceita somente os códigos de ACK suportados pelo Pub/Sub; neste projeto,
  **HTTP 204 No Content** é a resposta convencional após commit ou no-op válido.
- Resposta que não corresponda a ACK ou timeout provoca redelivery. Mensagem inválida destinada ao
  fluxo de tentativas/DLT não pode ser confirmada prematuramente.
- O endpoint deve respeitar timeout de Push e a variável `PORT`.
- A aplicação mantém semântica at-least-once e idempotência obrigatória.
- API e consumer podem escalar a zero e não exigem VM ou cluster.
