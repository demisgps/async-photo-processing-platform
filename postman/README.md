# Validação Postman — Fase 1

## Execução aprovada em 2026-09-10

A collection `async-photo-processing-platform.postman_collection.json` foi executada contra o
ambiente local iniciado por Docker Compose, com `baseUrl=http://localhost:8080`.

Arquivos utilizados:

- cadastro inicial: `C:\Users\Demis\Desktop\Velória\9.png`;
- substituição de foto: `C:\Users\Demis\Desktop\Velória\8.png`;
- limite excedido: fixture temporária de 10.485.761 bytes;
- formato não suportado: fixture textual temporária.

Resultados da execução final com Newman 6.1.3:

- 38 requests executados, incluindo repetições de polling;
- zero requests ou scripts com falha;
- cadastro `201` e processamento inicial até `PERSISTIDA`;
- foto atual `200`, com conteúdo de imagem não vazio;
- listagem, consulta e atualização de nome `200`;
- substituição de foto `202`, novo processamento até `PERSISTIDA` e nova foto `200`;
- exclusões de limpeza `204`;
- matriz negativa aprovada para `400`, `404`, `409`, `413` e `415`.

Os scripts da collection usam asserções Chai diretas, sem wrappers nomeados `pm.test`. Por isso o
resumo do Newman apresenta zero assertions nomeadas, embora os scripts tenham sido executados e a
execução terminasse com falha caso uma expectativa não fosse satisfeita.

A automação de polling e os caminhos locais das imagens foram aplicados somente a uma cópia
temporária usada pelo runner. A collection oficial permanece portátil e voltada à execução manual.
