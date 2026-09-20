# Networking e IAM

## Networking inicial

A primeira versão evita infraestrutura de rede dedicada:

- Cloud SQL com Public IP;
- Cloud SQL Connector para API e consumer;
- nenhuma authorized network aberta;
- sem Private IP, VPC Connector ou Cloud NAT;
- tráfego entre Pub/Sub e consumer por HTTPS autenticado;
- buckets sem acesso público;
- todos os recursos regionais em `us-central1` quando o serviço oferecer essa localização.

Public IP no Cloud SQL não significa conexão JDBC anônima exposta. São duas etapas independentes:

1. a service account de runtime e IAM autorizam o uso do Cloud SQL Connector, que estabelece o
   canal protegido sem authorized networks;
2. dentro do MySQL, a aplicação se autentica com usuário e senha, cuja senha fica no Secret Manager.

Permissão IAM para conexão não substitui a credencial do usuário MySQL. IAM Database Authentication
não será introduzida nesta fase.

Esta escolha reduz custo e quantidade de recursos. Private IP e Direct VPC egress podem ser
reavaliados caso surja requisito real de isolamento de rede.

## Exposição dos serviços

### photo-api

- permite invocação não autenticada;
- continua sem autenticação de usuário por decisão do MVP;
- deve ter limites de instância, concorrência, timeout e tamanho de request coerentes com o banco.

### photo-consumer

- não permite invocação não autenticada;
- `sa-pubsub-push` recebe `roles/run.invoker` somente nesse serviço;
- subscriptions Push usam token OIDC dessa identidade;
- endpoints principal e DLT são HTTPS autenticados e não públicos: continuam alcançáveis no
  endpoint do Cloud Run, mas IAM bloqueia chamadas não autenticadas;
- não há rede privada para o consumer nesta fase.

### photo-processor

- invocado somente pelo trigger Eventarc;
- não possui endpoint funcional público;
- usa sua própria identidade de runtime.

## Service accounts

As permissões abaixo são o desenho conceitual. Os papéis exatos serão confirmados ao escrever
Terraform; preferir grants no recurso (bucket, secret, serviço ou tópico) em vez de grants amplos no
projeto.

### sa-photo-api

- conectar ao Cloud SQL;
- ler somente o secret de credenciais do banco;
- criar/consultar/excluir objetos no bucket original;
- consultar/excluir objetos no bucket processado para a exclusão de usuário;
- sem permissão de publicar ou consumir Pub/Sub.

### sa-photo-consumer

- conectar ao Cloud SQL;
- ler somente o secret de credenciais do banco;
- ler objetos no bucket processado;
- sem escrita no bucket original ou processado;
- no modelo Push, não precisa consumir a subscription pela API StreamingPull.

### sa-photo-processor

- identidade de runtime da função, distinta da identidade do trigger;
- ler objetos do bucket original;
- consultar e criar objetos no bucket processado;
- publicar somente no tópico `foto-processada`;
- sem acesso ao Cloud SQL ou ao secret do banco.

### sa-eventarc-trigger

- identidade utilizada pelo trigger Eventarc para entregar o evento ao `photo-processor`;
- receber somente os papéis necessários ao Eventarc;
- invocar o destino autenticado;
- sem acesso aos buckets da aplicação, Cloud SQL, Secret Manager ou Pub/Sub da aplicação.

Os Google-managed service agents necessários ao Eventarc e ao Cloud Storage podem exigir papéis
próprios. Esses grants serão configurados posteriormente por Terraform nas identidades gerenciadas
corretas, sem reutilizar `sa-photo-processor` nem outra service account de runtime.

### sa-pubsub-push

- invocar somente o Cloud Run Service `photo-consumer`;
- sem acesso a Storage, Cloud SQL ou secrets.

O service agent do Pub/Sub precisa conseguir emitir o token OIDC quando exigido pela configuração
do projeto. Essa permissão deve ser concedida ao service agent, não ao runtime do consumer.

### sa-deployer

Identidade usada por CI ou operador para executar Terraform e deployments. Deve receber apenas os
papéis administrativos necessários aos recursos gerenciados e `serviceAccountUser` sobre as service
accounts que pode anexar. Não deve ser usada como identidade de runtime.

### Proteção de custo

Quando a automação for implementada, criar uma identidade separada, por exemplo
`sa-cost-protection`. Permissão para remover a associação de billing é altamente privilegiada e não
deve ser adicionada a nenhuma identidade da aplicação.

## Secret Manager

Armazenar inicialmente:

- senha do usuário MySQL da aplicação;
- outros segredos futuros somente quando houver consumidor real.

Não armazenar chaves JSON de service account. Cloud Run e Cloud Run functions devem usar a service
identity associada e Application Default Credentials. Variáveis não sensíveis, como nomes de bucket,
projeto e tópico, permanecem na configuração do serviço.

## Guardrails

- Public Access Prevention e Uniform Bucket-Level Access nos buckets.
- Nenhum papel básico `Owner` ou `Editor` para runtime.
- API pública não implica buckets, consumer ou banco públicos.
- Grants de Storage separados por bucket e responsabilidade.
- Secrets concedidos individualmente às identidades que os leem.
- Revisão dos IAM bindings pelo plano Terraform antes do apply.

## Referências oficiais

- [Opções de conexão ao Cloud SQL](https://docs.cloud.google.com/sql/docs/mysql/connection-options)
- [Conexão do Cloud Run ao Cloud SQL](https://docs.cloud.google.com/sql/docs/mysql/connect-run)
- [Autenticação de subscriptions Push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions)
- [Service identity no Cloud Run](https://docs.cloud.google.com/run/docs/securing/service-identity)
