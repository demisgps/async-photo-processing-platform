# ADR-002: Topologia e conectividade do Cloud SQL

- **Status**: Accepted
- **Data**: 2026-09-16

## Contexto

API e consumer persistem em MySQL 8.4. O ambiente é de estudo, prioriza custo e não exige alta
disponibilidade. Cloud Run possui endereços efêmeros, portanto abrir authorized networks não é uma
solução apropriada.

## Decisão

- Cloud SQL for MySQL 8.4 em `us-central1`.
- Instância pequena, single-zone, sem HA, réplicas ou backups.
- Public IP com Cloud SQL Connector: a service account de runtime e IAM autorizam a conexão e o
  Connector estabelece o canal protegido.
- Dentro do MySQL, a aplicação se autentica com usuário e senha; a senha fica no Secret Manager.
- Não utilizar IAM Database Authentication nesta fase.
- Nenhuma authorized network aberta.
- Sem Private IP, VPC Connector ou Cloud NAT nesta versão.
- Flyway executado no startup da API; consumer não executa migrations.

## Alternativas consideradas

### Private IP

Melhora isolamento de rede, mas exige recursos e configuração adicionais que não resolvem uma
necessidade atual do projeto.

### Serverless VPC Access e Cloud NAT

Adicionam custo e operação permanentes sem necessidade na primeira versão.

### Cloud Run Job para migrations

Separa migrations do runtime, mas cria novo artefato e fluxo de deployment. O startup da API é
suficiente para a escala atual.

### MySQL em VM

Rejeitado porque transferiria patches, backups, disponibilidade e segurança para o projeto.

## Consequências

- Cloud SQL representa custo contínuo mesmo quando Cloud Run escala a zero.
- Sem HA ou backups, indisponibilidade e perda de dados têm risco maior e são aceitas somente neste
  ambiente de estudo.
- API e consumer precisam integrar o Cloud SQL Connector e limitar pools/conexões.
- Permissão IAM para usar o Connector não substitui nem concede a autenticação do usuário dentro do
  MySQL.
- O primeiro rollout da API deve evitar concorrência desnecessária na execução do Flyway.
