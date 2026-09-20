# ADR-004: Terraform como fonte de verdade

- **Status**: Accepted
- **Data**: 2026-09-16

## Contexto

A arquitetura envolve IAM, Cloud Run, Function, Storage, Pub/Sub, Eventarc, Cloud SQL, secrets,
budgets e observabilidade. Criação exclusivamente manual dificulta reprodução, revisão e remoção
segura do ambiente.

## Decisão

- Terraform será a fonte de verdade da infraestrutura.
- O state remoto ficará em bucket GCS dedicado, com acesso restrito e versionamento.
- Console será usado para aprendizado, inspeção e troubleshooting.
- `gcloud` será usado para diagnóstico e operações pontuais.
- Mudanças permanentes feitas fora do Terraform devem ser importadas ou revertidas para evitar drift.

## Alternativas consideradas

### Console como fonte de verdade

Simples no primeiro clique, mas não reproduzível e sujeito a drift.

### Scripts gcloud

São úteis operacionalmente, mas exigem idempotência e não oferecem plano/dependências equivalentes
ao Terraform.

## Consequências

- O bucket de state exige um bootstrap inicial controlado.
- `terraform plan` passa a ser checkpoint obrigatório antes de apply.
- Secrets e state não podem entrar no Git.
- Permissões de `sa-deployer` precisam ser amplas o suficiente para provisionar, mas separadas das
  identidades de runtime.

