# Event Contracts

Os envelopes são Records imutáveis. Campos desconhecidos são tolerados; `schemaVersion` incompatível,
IDs ausentes ou referência inválida não são processados silenciosamente. Nenhum evento contém bytes.
O sistema não depende da ordem de entrega do Pub/Sub: `processamentoId`, `sequencia_upload`, state
machine, CAS/locks e tratamento idempotente preservam ordem lógica e monotonicidade.

## Storage Object Finalized CloudEvent

- Type: `google.cloud.storage.object.v1.finalized`
- Subject/source/id/time conforme CloudEvents/Eventarc.
- Data mínima: `bucket`, `name`, `generation`, `contentType`, `size`, `crc32c`/`md5Hash`, metadados
  `usuarioId` e `processamentoId`.
- Apenas `fotos-usuarios-original` e padrão de chave esperado são aceitos.
- Dedupe técnico do dispatcher local: `bucket + name + generation`; idempotência de negócio:
  `processamentoId`.

## PhotoProcessingResult v1

Tópico: `foto-processada`.

```json
{
  "schemaVersion": 1,
  "eventId": "uuid-da-publicacao",
  "occurredAt": "2026-09-07T12:00:00Z",
  "processamentoId": "550e8400-e29b-41d4-a716-446655440000",
  "usuarioId": 1,
  "status": "PROCESSADA",
  "processedObject": {
    "bucket": "fotos-usuarios-processadas",
    "name": "1/550e8400-e29b-41d4-a716-446655440000/arquivo.jpg",
    "generation": "12345",
    "contentType": "image/jpeg",
    "size": 123456,
    "checksum": "base64-checksum",
    "width": 1024,
    "height": 768
  }
}
```

`eventId` identifica a tentativa/publicação; `processamentoId` é a chave idempotente. Republicar
resultado existente pode gerar novo `eventId`, mas o mesmo conteúdo lógico e referência.

## PhotoProcessingError v1

Usa o mesmo tópico para permitir uma única subscription e tratamento uniforme pelas regras de
estado, sem pressupor ordem de entrega.

```json
{
  "schemaVersion": 1,
  "eventId": "uuid-da-publicacao",
  "occurredAt": "2026-09-07T12:00:00Z",
  "processamentoId": "550e8400-e29b-41d4-a716-446655440000",
  "usuarioId": 1,
  "status": "ERRO_PROCESSAMENTO",
  "error": {
    "code": "INVALID_IMAGE_CONTENT",
    "message": "Imagem não pôde ser processada",
    "transient": false
  },
  "originalObject": {
    "bucket": "fotos-usuarios-original",
    "name": "1/550e8400-e29b-41d4-a716-446655440000/arquivo.jpg",
    "generation": "123"
  }
}
```

Mensagem de erro definitiva não carrega stack trace, segredo ou bytes. Evento atrasado para terminal
é ACK/no-op rastreável.

Uma mensagem válida para o mesmo `processamentoId` em `PERSISTINDO` não é considerada atrasada,
fora de ordem ou no-op: ela retoma idempotentemente a persistência final. Dados já existentes são
validados/reutilizados, o BLOB não é duplicado e o commit conclui metadados, promoção e
`PERSISTIDA`; falha definitiva registrável leva a `ERRO_PERSISTENCIA`.

## Consumer and DLT semantics

- ACK somente após commit de sucesso ou decisão idempotente realmente terminal/inválida.
- Transitório: NACK/exceção; broker redelivera.
- Após 8 tentativas best-effort: encaminhamento a `foto-processada-dlq` preserva payload e atributos.
- Atributos mínimos: `schemaVersion`, `processamentoId`, `usuarioId`, `eventType`.
- Handler DLT tenta `ERRO_PERSISTENCIA` se estado ainda permitir; banco indisponível causa NACK na
  subscription DLT. Retenção planejada: 7 dias; sem segunda DLQ.
- Tópico único centraliza sucesso e erro, mas não implica ordenação. Duplicatas, atrasos e mensagens
  fora de ordem nunca regridem estado nem promovem foto antiga. Se reconciliação já mudou
  condicionalmente para `ERRO_PROCESSAMENTO`, resultado tardio é ACK/no-op rastreável.
