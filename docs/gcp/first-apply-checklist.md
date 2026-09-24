# Checklist do primeiro provisionamento GCP

Este checklist documenta comandos futuros. Nada aqui deve ser executado sem revisão do projeto,
custos, IAM e plano Terraform.

## Pré-requisitos

- confirmar projeto, região `us-central1` e conta de faturamento;
- autenticar a identidade humana via ADC;
- confirmar permissões para Service Usage, Terraform state, budgets e recursos do projeto;
- confirmar no billing account as permissões `billing.budgets.create`, `get`, `list`, `update` e
  `delete`, além de acesso para consultar o projeto usado no filtro;
- confirmar `iam.serviceAccounts.actAs` sobre `sa-photo-api`, `sa-photo-consumer`,
  `sa-photo-processor`, `sa-photo-processor-builder`, `sa-eventarc-trigger` e `sa-pubsub-push`;
- validar na documentação atual a compatibilidade de `MYSQL_8_4`, `ENTERPRISE`, `db-f1-micro`,
  `us-central1` e o tipo de disco efetivamente selecionado pelo Cloud SQL;
- nunca colocar senha, token, chave JSON ou `DB_PASSWORD` em tfvars, plano ou Git.

## 1. Bootstrap do remote state

Em `infra/terraform/bootstrap`:

```bash
terraform init
terraform plan -out=bootstrap.tfplan
# revisar integralmente
terraform apply bootstrap.tfplan
```

Esse apply cria somente `${project_id}-tfstate`, com UBLA, Public Access Prevention, versioning,
`force_destroy=false` e `prevent_destroy`.

## 2. Configurar backend do study

Copiar `infra/terraform/study/backend.hcl.example` para `backend.hcl`, inserir o bucket real e não
versionar o arquivo:

```bash
cd infra/terraform/study
terraform init -backend-config=backend.hcl
```

Como ainda não existe state do study, não usar `-migrate-state` automaticamente.

## 3. Habilitar APIs

Antes de depender de service agents, executar excepcionalmente um apply direcionado:

```bash
terraform plan -target='google_project_service.required' \
  -var='project_id=<PROJECT_ID>' \
  -var='api_image=bootstrap.invalid/photo-api@sha256:0000000000000000000000000000000000000000000000000000000000000000' \
  -var='consumer_image=bootstrap.invalid/photo-consumer@sha256:0000000000000000000000000000000000000000000000000000000000000000' \
  -var='function_source_zip=/dev/null' \
  -out=apis.tfplan
# revisar: somente google_project_service.required[*]
terraform apply apis.tfplan
```

Os placeholders existem apenas porque o root declara inputs obrigatórios de workloads. Eles não
podem ser salvos em tfvars nem usados num full apply. `-target` é exceção de bootstrap, não fluxo
operacional normal.

## 4. Verificar managed service agents

Após propagação das APIs, apenas inspecionar a existência e os papéis padrão de:

- Pub/Sub service agent;
- Eventarc service agent;
- Cloud Storage service agent;
- Cloud Functions service agent;
- Cloud Build service agent, quando aplicável.

Não atribuir papel de service agent a service accounts user-managed.

## 5. Criar o budget antes do Cloud SQL

Copiar `infra/terraform/billing/backend.hcl.example` para `backend.hcl`, inserir o mesmo bucket de
state e não versionar valores da conta:

```bash
cd infra/terraform/billing
terraform init -backend-config=backend.hcl
terraform plan \
  -var='project_id=<PROJECT_ID>' \
  -var='billing_account_id=<BILLING_ACCOUNT_ID>' \
  -var='budget_amount=<AMOUNT_BRL>' \
  -out=billing.tfplan
# revisar integralmente
terraform apply billing.tfplan
```

Confirmar budget mensal de R$ 150 (BRL), escopo de um projeto, gasto bruto, thresholds 50/80/90/100
e destinatários IAM padrão. Budget não interrompe consumo e não garante limite máximo de cobrança.

## 6. Provisionar a fundação em duas etapas

Cloud SQL é o principal recurso desta arquitetura com custo contínuo enquanto permanece
provisionado. Por isso, a fundação é separada em dois plans/applies revisados de forma independente.

### Fundação 1 — recursos básicos sem Cloud SQL

O primeiro apply parcial do root `study` cria somente identidades, Artifact Registry, os três
buckets, o container do secret, os tópicos Pub/Sub e o IAM básico que independe dos workloads e do
Cloud SQL. Usar os mesmos placeholders efêmeros da etapa de APIs e direcionar exatamente estes
recursos:

```text
google_service_account.application
google_artifact_registry_repository.application
google_storage_bucket.original
google_storage_bucket.processed
google_storage_bucket.function_source
google_secret_manager_secret.db_password
google_pubsub_topic.photo_processed
google_pubsub_topic.photo_processed_dlt
google_storage_bucket_iam_member.photo_api_original_object_admin
google_storage_bucket_iam_member.photo_api_processed_object_admin
google_storage_bucket_iam_member.photo_consumer_processed_object_viewer
google_storage_bucket_iam_member.photo_processor_original_object_viewer
google_storage_bucket_iam_member.photo_processor_processed_object_admin
google_secret_manager_secret_iam_member.photo_api_db_password_accessor
google_secret_manager_secret_iam_member.photo_consumer_db_password_accessor
google_pubsub_topic_iam_member.photo_processor_result_publisher
google_storage_bucket_iam_member.photo_processor_builder_source_viewer
google_artifact_registry_repository_iam_member.photo_processor_builder_writer
google_project_iam_member.photo_processor_builder_log_writer
```

Construir o comando `terraform plan` com um `-target=<endereço>` para cada endereço acima, revisar
que nenhum Cloud SQL, workload, subscription ou trigger entrou no plano e só então aplicar o plan
salvo. Dependências referenciadas podem ser incluídas automaticamente e também devem ser revisadas.

Os buckets têm responsabilidades intencionalmente separadas:

1. `original`: recebe a imagem original enviada pela API e será a origem do Eventarc;
2. `processed`: recebe a imagem processada/redimensionada pela `photo-processor`;
3. `function_source`: armazena exclusivamente o ZIP/source usado no build e deployment da função.

Artefatos de build da Function não devem ser misturados com os buckets funcionais de fotos.

### Fundação 2 — Cloud SQL isolado

Gerar e revisar separadamente o plan contendo somente:

```text
google_sql_database_instance.main
google_sql_database.application
google_project_iam_member.photo_api_cloud_sql_client
google_project_iam_member.photo_consumer_cloud_sql_client
```

Antes do apply, revisar isoladamente configuração, compatibilidade e impacto de custo do Cloud SQL.
Não criar `google_sql_user`: usuário e senha permanecem no checkpoint operacional fora do Terraform.

Os states parciais não representam o estado final desejado. Não executar operações rotineiras com
`-target`; após os checkpoints seguintes, um full plan é obrigatório.

## 7. Checkpoint operacional

### Cloud SQL

1. Criar o usuário MySQL fora do Terraform.
2. Gerar senha segura sem registrá-la em shell history ou arquivo versionado.
3. Adicionar uma versão ao secret `photo-platform-study-db-password` fora do Terraform.

### Artifact Registry

1. Construir `photo-api` e `photo-consumer`.
2. Publicar imagens imutáveis no Artifact Registry.
3. Registrar referências `@sha256`, sem depender de `latest`.

### Function

```bash
backend/scripts/package-photo-processor-source.sh
```

Validar conteúdo e SHA-256 do `photo-processor-source.zip`.

## 8. Preparar inputs reais

Fornecer sem versionar:

- `api_image` com digest;
- `consumer_image` com digest;
- `function_source_zip` apontando para o ZIP local.

Não fornecer `DB_PASSWORD` ao Terraform.

## 9. Full plan

No root `study`, executar sem `-target`:

```bash
terraform plan -out=study.tfplan
```

Revisar recursos, IAM, deletion protection, imagens, source checksum, Cloud SQL, Function,
Eventarc, subscriptions Push, OIDC e DLT. Não aplicar automaticamente.

## 10. Full apply

Somente depois da revisão:

```bash
terraform apply study.tfplan
```

## 11. Validação funcional cloud

- API pública e readiness;
- Cloud SQL Java Connector e Flyway;
- upload no bucket original;
- evento `object finalized` via Eventarc;
- execução da Function e objeto processado;
- publicação no tópico;
- Push OIDC principal e DLT;
- consumer, BLOB no MySQL e estado `PERSISTIDA`;
- redelivery, idempotência e encaminhamento à DLT.

## 12. Observabilidade pós-deployment

1. Examinar Cloud Logging e correlação por `processamentoId`.
2. Observar métricas nativas de Cloud Run, Function, Pub/Sub e Cloud SQL.
3. Obter baseline de latência, 5xx, CPU, memória, conexões e backlog.
4. Somente então definir dashboard e alert policies úteis via Terraform.

## Guardrails finais

- Budget tradicional é alerta, não hard cap.
- Spend Cap Budget Preview não cobre Cloud SQL atualmente.
- Kill switch de billing e `sa-cost-protection` permanecem adiados.
- Todo apply usa plan salvo e revisado.
- Depois de qualquer bootstrap com `-target`, executar full plan sem `-target`.
