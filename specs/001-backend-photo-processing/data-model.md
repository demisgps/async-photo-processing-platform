# Data Model: Backend de Processamento Assíncrono de Fotos

## USUARIO

| Campo | Tipo lógico / MySQL | Regras |
|---|---|---|
| `id` | BIGINT | PK, `AUTO_INCREMENT`, imutável |
| `nome` | VARCHAR(150) | 2–150 caracteres Unicode após trim, obrigatório |
| `foto_atual_processamento_id` | BINARY(16), nullable | FK que garante apenas existência do processamento referenciado |
| `proxima_sequencia_upload` | BIGINT | contador monotônico alocado sob lock, inicia em 1 |
| `version` | BIGINT | optimistic version |
| `data_cadastro` | DATETIME(6) UTC | obrigatório, imutável |
| `data_atualizacao` | DATETIME(6) UTC | atualizado em mutações |

## PROCESSAMENTO_FOTO

| Campo | Tipo lógico / MySQL | Regras |
|---|---|---|
| `id` | BINARY(16) | PK; UUID `processamentoId` |
| `usuario_id` | BIGINT | FK `USUARIO`, obrigatório |
| `sequencia_upload` | BIGINT | unique por usuário; crescente |
| `status` | VARCHAR(32) | enum validado por CHECK |
| `usuario_ativo_id` | BIGINT gerado, nullable | `usuario_id` em estado ativo; NULL em terminal |
| `bucket_original` / `arquivo_original` | VARCHAR | referência obrigatória após aceite |
| `geracao_original` / `checksum_original` | VARCHAR | identidade imutável do objeto |
| `bucket_processado` / `arquivo_processado` | VARCHAR, nullable | preenchido a partir de `PROCESSADA` |
| `geracao_processada` / `checksum_processada` | VARCHAR, nullable | valida reutilização segura |
| `imagem_processada` | LONGBLOB, nullable | bytes persistidos no estágio final |
| `content_type` | VARCHAR(64) | `image/jpeg` ou `image/png` |
| `nome_arquivo` | VARCHAR | nome sanitizado para metadado/exibição |
| `largura` / `altura` | INT, nullable | <=1024 quando processada |
| `erro_codigo` / `erro_detalhe` | VARCHAR/TEXT, nullable | sem segredo; requerido em erro registrável |
| `version` | BIGINT | optimistic version/CAS |
| `data_recebimento` | DATETIME(6) UTC | criação |
| `data_inicio_processamento` | DATETIME(6), nullable | `PROCESSANDO` |
| `data_processamento` | DATETIME(6), nullable | `PROCESSADA` |
| `data_inicio_persistencia` | DATETIME(6), nullable | `PERSISTINDO` |
| `data_persistencia` | DATETIME(6), nullable | `PERSISTIDA` |
| `data_erro` | DATETIME(6), nullable | estado terminal de erro |

### Constraints and indexes

- `UNIQUE(usuario_id, sequencia_upload)` ordena candidatos.
- `UNIQUE(usuario_ativo_id)` garante um ativo por usuário; MySQL admite múltiplos NULL.
- Índices: `(usuario_id, status)`, `(status, data_inicio_processamento)` e
  `foto_atual_processamento_id`.
- FK processamento -> usuário bloqueia remoção acidental; a exclusão controlada remove filhos antes
  do pai. A FK de foto atual é adicionada após ambas as tabelas e deve ser anulada dentro da
  transação de exclusão.
- A FK de foto atual, isoladamente, não garante mesmo usuário, elegibilidade ou `PERSISTIDA`. Essas
  invariantes são verificadas sob locks/CAS pela promoção transacional e por testes de integração.
- CHECKs associam campos processados/erro aos estados correspondentes sem exigir dados impossíveis
  quando a comunicação falhar completamente.

## State Machine

```text
RECEBIDA -> PROCESSANDO -> PROCESSADA -> PERSISTINDO -> PERSISTIDA
                    \-> ERRO_PROCESSAMENTO
PROCESSADA  ---------\
                      -> ERRO_PERSISTENCIA
PERSISTINDO ---------/
```

| From | To | Owner / condition |
|---|---|---|
| novo | `RECEBIDA` | API validou nome e foto |
| `RECEBIDA` | `PROCESSANDO` | API confirmou original; condição do aceite HTTP |
| `PROCESSANDO` | `PROCESSADA` | Consumer recebe sucesso publicado pelo processor |
| `PROCESSANDO` | `ERRO_PROCESSAMENTO` | Consumer recebe erro definitivo publicado pelo processor |
| `PROCESSADA` | `PERSISTINDO` | Consumer vence CAS/lock |
| `PERSISTINDO` | `PERSISTIDA` | Entrega inicial ou redelivery retoma; BLOB/metadados e promoção commitam juntos |
| `PROCESSADA`/`PERSISTINDO` | `ERRO_PERSISTENCIA` | falha definitiva/esgotada registrável |

Estados terminais nunca transitam. Evento repetido, antigo ou fora de ordem é no-op rastreável.
Enquanto a falha de publicação não for detectável/registrável, `PROCESSANDO` permanece ativo e
bloqueia upload/exclusão. Não há reconciliador automático nesta fase; um processamento que não seja
recuperado por retry, redelivery ou reexecução segura requer tratamento operacional.

`PERSISTINDO` não é terminal nem no-op: somente um `PhotoProcessingResult` equivalente do mesmo
`processamentoId` retoma a persistência final, valida e reutiliza dados existentes e evita duplicar
o BLOB. Conclui promoção, metadados e `PERSISTIDA` em transação, ou chega a
`ERRO_PERSISTENCIA` se a falha definitiva puder ser registrada. Um `PhotoProcessingError` recebido
em `PERSISTINDO` é ACK/no-op rastreável e não regride para `ERRO_PROCESSAMENTO`. ACK ocorre apenas
após commit ou no-op de mensagem contratualmente válida duplicada, atrasada, fora de ordem,
terminal ou logicamente não aplicável. Mensagem malformada ou contratualmente inválida falha/NACK e
segue redelivery finita até a DLT.

## Atomic promotion

1. Lock de processamento e usuário, aceitando retomada de `PERSISTINDO` para o mesmo ID.
2. Confirmar estado/candidato e que `sequencia_upload` é maior que a foto atual.
3. Validar/reutilizar BLOB e metadados existentes ou persistir os ausentes sem duplicação.
4. Atualizar `USUARIO.foto_atual_processamento_id`.
5. Alterar processamento para `PERSISTIDA`.
6. Commit único; qualquer falha reverte os passos 3–5.

## Idempotency decisions

- Processor: objeto determinístico + metadados origem/algoritmo + create-if-absent.
- Consumer: PK por `processamentoId`, locks/CAS e ACK depois do commit.
- `PhotoProcessingResult` equivalente em `PERSISTINDO`: retoma, não regride e não é classificado como
  mensagem fora de ordem; `PhotoProcessingError` nesse estado é ACK/no-op rastreável.
- Promoção: sequência monotônica impede foto antiga; terminal equivalente é ACK/no-op.
- Exclusão: chaves permanecem consultáveis até limpeza integral; objeto ausente equivale a removido.

## Flyway outline

- `V1__create_usuario_and_processamento_foto.sql`: tabelas, enum CHECKs, FKs e índices.
- `V2__add_active_processing_guard.sql`: coluna gerada e unique ativo, caso a expressão precise ser
  separada por compatibilidade MySQL 8.4.
- Nenhuma migration destrutiva ou automática fora do Flyway.
