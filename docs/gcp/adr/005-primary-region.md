# ADR-005: Região principal us-central1

- **Status**: Accepted
- **Data**: 2026-09-16

## Contexto

O projeto é de estudo e portfólio, com prioridade maior para disponibilidade dos serviços e baixo
custo do que para latência ao usuário brasileiro. Recursos distribuídos entre regiões aumentariam
latência, egress e complexidade.

## Decisão

Usar `us-central1` como região principal e colocalizar Cloud Run, Cloud Run Function, buckets,
Eventarc, Artifact Registry e Cloud SQL sempre que suportado.

## Alternativas consideradas

### southamerica-east1

Oferece menor latência para o Brasil, mas não é a prioridade desta fase e pode possuir diferenças
de preço/franquia que precisam ser avaliadas.

### Recursos em regiões diferentes

Rejeitado por latência, egress e restrições de localização do Eventarc.

## Consequências

- Usuários no Brasil terão latência maior do que em uma região sul-americana.
- A arquitetura fica mais simples e reduz tráfego inter-regional.
- Disponibilidade e preço de todos os SKUs devem ser confirmados imediatamente antes do Terraform.

