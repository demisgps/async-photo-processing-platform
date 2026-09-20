# ADR-003: Storage, Eventarc e retenção

- **Status**: Accepted
- **Data**: 2026-09-16

## Contexto

O processamento precisa preservar a original separadamente da processada, evitar bytes no Pub/Sub
e reagir à finalização do upload. Localmente, fake-gcs-server e um dispatcher simulam esse evento.
Os objetos são temporários porque a foto atual é persistida como BLOB no MySQL.

## Decisão

- Manter dois buckets: original e processado.
- Somente o bucket original emite trigger Eventarc `object finalized` para o processor.
- Desabilitar object versioning.
- Configurar lifecycle `Delete` com idade de 3 dias nos dois buckets.
- Manter chaves determinísticas por `usuarioId/processamentoId` e precondições create-only.
- Não implantar o `storage-event-dispatcher` na GCP.

## Alternativas consideradas

### Um único bucket

Reduz um recurso, mas aumenta risco de trigger recursivo, mistura IAM/lifecycle e reduz clareza
operacional.

### Enviar imagem pelo Pub/Sub

Rejeitado por acoplamento, tamanho da mensagem, custo e violação do contrato atual.

### Manter objetos indefinidamente

Facilitaria investigação, mas acumularia custo sem necessidade funcional neste ambiente.

## Consequências

- O processor e consumer continuam idempotentes porque Eventarc/Pub/Sub podem redeliverar.
- Tanto a imagem original quanto a processada são objetos temporários.
- Após a exclusão por lifecycle, reprocessamento que dependa da original pode não ser possível.
- Se uma mensagem permanecer em retry/DLT ou receber tratamento operacional depois da remoção da
  processada, o consumer pode não conseguir baixá-la para persistir o BLOB.
- Neste ambiente de estudo, a janela limitada de recuperação é aceita e pode exigir novo upload ou
  intervenção manual.
- A foto atual continua sendo servida pelo BLOB do MySQL.
- Lifecycle não garante remoção exatamente no instante em que o objeto completa 3 dias.
