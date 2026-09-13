# Event Contracts

Os envelopes são Records imutáveis. Campos desconhecidos são tolerados; payload não desserializável,
`schemaVersion` incompatível, IDs obrigatórios ausentes ou referência estruturalmente inválida não
são processados nem descartados silenciosamente: falham/NACK e seguem redelivery finita até a DLT.
Nenhum evento contém bytes.
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

Somente um `PhotoProcessingResult` equivalente para o mesmo `processamentoId` em `PERSISTINDO` não
é considerado atrasado, fora de ordem ou no-op: ele retoma idempotentemente a persistência final.
Dados já existentes são validados/reutilizados, o BLOB não é duplicado e o commit conclui
metadados, promoção e `PERSISTIDA`; falha definitiva registrável leva a `ERRO_PERSISTENCIA`. Um
`PhotoProcessingError` recebido quando o processamento já está em `PERSISTINDO` é ACK/no-op
rastreável e não pode causar regressão para `ERRO_PROCESSAMENTO`. Estados terminais nunca regridem.

## Consumer and DLT semantics

- ACK somente após commit de sucesso ou decisão idempotente sobre mensagem contratualmente válida
  duplicada, atrasada, fora de ordem, terminal ou logicamente não aplicável pelas regras monotônicas.
- Falha transitória ou mensagem malformada/contratualmente inválida: NACK/exceção; broker redelivera
  de forma finita até a DLT.
- Após 8 tentativas best-effort: encaminhamento a `foto-processada-dlq` preserva payload e atributos.
- Quando presentes ou recuperáveis, preservar `schemaVersion`, `processamentoId`, `usuarioId`,
  `eventId` e `eventType`; `processamentoId` permanece a chave principal quando disponível.
- Para mensagem malformada sem `processamentoId` recuperável, preservar Pub/Sub message ID,
  `eventId` quando recuperável, atributos disponíveis e payload bruto original para investigação,
  sem inventar `processamentoId`.
- Handler DLT tenta `ERRO_PERSISTENCIA` se estado ainda permitir; banco indisponível causa NACK na
  subscription DLT. Retenção planejada: 7 dias; sem segunda DLQ.
- Tópico único centraliza sucesso e erro, mas não implica ordenação. Duplicatas, atrasos e mensagens
  fora de ordem nunca regridem estado nem promovem foto antiga. Resultado tardio para qualquer
  estado terminal é ACK/no-op rastreável.
