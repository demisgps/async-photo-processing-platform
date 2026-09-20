# Plano futuro de deployment

Este documento define ordem e checkpoints. Ele não executa comandos nem provisiona recursos.

## 1. Adaptações de código

1. Validar os adapters HTTP já criados para envelopes Pub/Sub Push principal e DLT no `photo-consumer`.
2. Preservar HTTP 204 somente após commit/no-op válido; resposta que não corresponda a ACK ou timeout
   permite redelivery, inclusive para contrato inválido destinado a tentativas/DLT.
3. Preservar serviços, idempotência, retomada de `PERSISTINDO`, ordenação e DLT existentes.
4. Validar a configuração já adaptada: endpoint local explícito no Compose e endpoint padrão/ADC
   quando `STORAGE_ENDPOINT` estiver ausente na GCP.
5. Adicionar Cloud SQL Connector/configuração JDBC à API e consumer.
6. Externalizar projeto, região, buckets, tópico, subscriptions e connection name.
7. Revisar imagens de container para runtime, usuário não-root quando aplicável e shutdown.
8. Não reintroduzir reconciliador.

## 2. Validação local

1. Usar no Pub/Sub Emulator o mesmo adapter HTTP Push destinado à cloud.
2. Testar o envelope Push sem criar E2E obrigatório; autenticação OIDC pertence à infraestrutura GCP.
3. Executar testes dos módulos afetados e `./mvnw verify` ao final.
4. Subir Docker Compose e executar a collection Postman.
5. Confirmar retry, redelivery, DLT, duplicatas, fora de ordem e retomada.

### Estado atual das validações técnicas

| Prática | Estado atual | Ação futura |
|---|---|---|
| Testes de integração com Testcontainers | Implementados para MySQL 8.4 na API e consumer | Preservar no quality gate durante as adaptações cloud |
| Testes arquiteturais com ArchUnit | Não implementados | Avaliar e, se adotado, validar somente regras arquiteturais duráveis após a adaptação Push, sem criar testes artificiais |
| Testes de carga | Não executados | Executar depois do primeiro deployment funcional na GCP |
| Benchmarking de performance | Não executado; tempos de build/teste não constituem benchmark | Criar medição repetível de latência, throughput, recursos e custo no ambiente cloud |

ArchUnit, caso seja adotado, deve proteger regras relevantes como separação entre componentes,
fluxo Controller → Service → Repository e ausência de acesso MySQL pelo processor. Ele não deve
ser introduzido apenas para aumentar quantidade de testes.

## 3. Terraform

1. Definir estrutura de ambientes e providers.
2. Criar bootstrap mínimo do bucket de state remoto com versionamento e acesso restrito.
3. Importar ou evitar recursos criados manualmente; Terraform deve ser a fonte de verdade.
4. Modelar APIs, service accounts, IAM, Artifact Registry, buckets, Pub/Sub/Eventarc, Cloud SQL,
   secrets, Cloud Run e Function.
5. Revisar `terraform plan`, custos e IAM antes de qualquer apply.

Console fica reservado para aprendizado, inspeção e troubleshooting; `gcloud`, para diagnóstico e
operações pontuais reproduzíveis.

## 4. Provisionamento base

1. Configurar projeto, billing e budget com alertas antecipados.
2. Habilitar somente APIs necessárias.
3. Criar service accounts e IAM de menor privilégio.
4. Criar Artifact Registry e Secret Manager.
5. Criar os dois buckets em `us-central1`, sem versioning e com lifecycle de 3 dias.
6. Criar Cloud SQL MySQL 8.4 single-zone e o usuário/schema da aplicação.
7. Criar tópico, subscriptions Push, DLT e subscription DLT.

Não habilitar ainda a função destrutiva de proteção de billing; primeiro validar alertas em
dry-run.

## 5. Build das imagens

1. Executar quality gate Maven.
2. Construir imagens imutáveis de API e consumer.
3. Identificar imagens por commit SHA/tag, sem depender apenas de `latest`.
4. Fazer scan/revisão básica e registrar digests.

## 6. Artifact Registry

1. Publicar imagens na região escolhida.
2. Conceder leitura apenas às identidades/plataformas necessárias.
3. Configurar política de limpeza para artefatos antigos.

## 7. Deploy

Ordem recomendada:

1. processor e identidade, ainda sem trigger ativo;
2. consumer privado e endpoints principal/DLT;
3. API com limite inicial de instâncias baixo;
4. startup da API aplica Flyway antes de servir tráfego;
5. smoke de health e conectividade com Cloud SQL/Storage.

O primeiro rollout da API deve evitar concorrência desnecessária de migrations. Flyway permanece no
startup; não será criado Cloud Run Job nesta fase.

## 8. Eventarc e Pub/Sub

1. Configurar Push OIDC com `sa-pubsub-push` e `roles/run.invoker` no consumer.
2. Configurar redelivery, retenção e DLT conforme os budgets já validados localmente.
3. Ligar Eventarc somente ao bucket original e ao evento `object finalized`.
4. Confirmar que o bucket processado não dispara a função.
5. Confirmar que mensagens contêm somente referências/metadados.

## 9. Validação funcional

1. Executar cadastro e aguardar `PERSISTIDA`.
2. Consultar foto atual.
3. Testar substituição, duplicata, evento atrasado e exclusão.
4. Validar respostas negativas 400, 404, 409, 413 e 415.
5. Validar redelivery e DLT sem enfraquecer idempotência.
6. Confirmar lifecycle e ausência de objetos versionados.

## 10. Observabilidade

1. Criar dashboard com sinais de Cloud Run, SQL e Pub/Sub.
2. Criar alertas de 5xx, backlog, DLT, conexões e budget.
3. Validar correlação ponta a ponta por `processamentoId`.
4. Exercitar o runbook de mensagem em DLT e processamento ativo preso.
5. Validar a proteção de billing somente em dry-run.

## 11. Testes de carga e custo

Esta etapa ainda não foi realizada. A ferramenta e o perfil de carga serão escolhidos somente antes
da execução, evitando adicionar dependência sem necessidade.

1. Definir cenários, volume, duração, massa de imagens e critérios de aceitação reproduzíveis.
2. Medir cold start, throughput, p50/p95/p99 e consumo de CPU/memória.
3. Ajustar concorrência, instâncias máximas e pools JDBC em conjunto.
4. Medir backlog, delivery attempts e tempo total até `PERSISTIDA`.
5. Verificar que retries não multiplicam chamadas excessivamente.
6. Registrar configuração, resultados e limitações para permitir comparação futura.
7. Conferir custos reais e projeção antes de ampliar limites.

## Critério de conclusão

Deployment somente é considerado validado quando o fluxo completo, matriz negativa, resiliência,
IAM, logs, alertas e limites de custo estiverem confirmados sem permissões básicas de projeto nos
runtimes.
