# Arquitetura GCP

Esta pasta descreve a primeira implantação do `async-photo-processing-platform` na Google Cloud.
Ela registra decisões arquiteturais; não contém recursos provisionados nem substitui os contratos
funcionais em `specs/001-backend-photo-processing/`.

O plano e o research da Fase 1 registram corretamente que o runtime de API e consumer estava
adiado para a etapa de deployment. Os ADRs desta pasta são a resolução posterior dessa decisão, sem
reescrever o histórico da Fase 1.

## Documentos

- [Arquitetura](architecture.md): componentes, fluxos, persistência e diferenças entre local e GCP.
- [Networking e IAM](networking-iam.md): conectividade mínima, identidades e menor privilégio.
- [Observabilidade e custos](observability-cost.md): sinais operacionais, dashboards e guardrails.
- [Plano de deployment](deployment-plan.md): sequência futura de adaptação e implantação.
- [ADRs](adr/): decisões aceitas e seus trade-offs.

## Escopo aprovado

- Região principal: `us-central1`.
- `photo-api`: Cloud Run Service público, com scale-to-zero.
- `photo-consumer`: Cloud Run Service privado, invocado por Pub/Sub Push autenticado.
- `photo-processor`: Cloud Run Function acionada por Eventarc.
- Banco: Cloud SQL for MySQL 8.4, single-zone, sem HA, réplicas ou backups nesta fase.
- Objetos: dois buckets Cloud Storage com exclusão por lifecycle após 3 dias.
- Infraestrutura futura: Terraform como fonte de verdade.
- Observabilidade: Cloud Logging e Cloud Monitoring.

Não fazem parte desta arquitetura: GCE, GKE, Kubernetes, Service Mesh, Prometheus, Grafana e o
reconciliador de processamentos estagnados.

## Estado da implementação

Estes documentos descrevem o alvo. O ambiente local continua usando Docker Compose, MySQL,
fake-gcs-server, Pub/Sub Emulator, Functions Framework e `storage-event-dispatcher`. As adaptações
necessárias para o alvo cloud estão listadas no plano de deployment e ainda não foram implementadas.
