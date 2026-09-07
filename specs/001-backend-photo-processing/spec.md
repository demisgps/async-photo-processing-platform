# Feature Specification: Backend de Processamento Assíncrono de Fotos — Fase 1

**Feature Branch**: `001-backend-photo-processing`
**Created**: 2026-09-06
**Status**: Ready for Planning
**Input**: Especificar exclusivamente o backend da Fase 1 conforme a documentação principal e a
constituição do projeto.

## Clarifications

### Session 2026-09-07

- Q: Quando a publicação do resultado falhar definitivamente e o processamento ficar sem
  comunicação após todas as tentativas, ele deve continuar bloqueando novos uploads e a exclusão
  do usuário? → A: Quando a falha definitiva puder ser detectada e registrada posteriormente,
  transita para `ERRO_PROCESSAMENTO`; até então continua bloqueando.
- Q: Quando uma mensagem do `photo-consumer` esgotar as tentativas e for encaminhada ao Dead Letter
  Topic, qual estado o processamento deve assumir quando ainda for possível registrá-lo? → A:
  Registrar `ERRO_PERSISTENCIA` e encaminhar a mensagem ao Dead Letter Topic.
- Q: Como o sistema deve tratar uma mensagem atrasada ou fora de ordem referente a um processamento
  que já está terminal ou que não corresponde mais à foto candidata atual? → A: Não alterar estado
  nem foto atual; registrar a ocorrência de forma rastreável.
- Q: Se a exclusão remover apenas parte dos objetos ou dados associados antes de falhar, quando ela
  deve ser considerada bem-sucedida? → A: Retornar falha enquanto houver dados associados; novas
  tentativas retomam a remoção com segurança.
- Q: Se a imagem e os metadados forem persistidos, mas a promoção dessa imagem como foto atual
  falhar, qual deve ser o resultado funcional do processamento? → A: Permanecer fora de
  `PERSISTIDA`; repetir com segurança e, se esgotar, registrar `ERRO_PERSISTENCIA`.
- Q: Quais status devem indicar sucesso no cadastro inicial e no upload posterior, considerando que
  ambos iniciam processamento assíncrono? → A: Cadastro retorna HTTP 201; upload posterior retorna
  HTTP 202.
- Q: Quais status devem indicar sucesso na atualização do nome e na exclusão completa do usuário?
  → A: Atualização retorna HTTP 200 com o usuário atualizado; exclusão retorna HTTP 204 sem
  conteúdo.
- Q: Quais status devem ser usados para nome inválido, foto obrigatória ausente, múltiplas imagens,
  formato não suportado e arquivo acima de 10 MiB? → A: HTTP 400 para nome inválido, foto ausente
  ou múltiplas imagens; HTTP 415 para formato não suportado; HTTP 413 para excesso de tamanho.
- Q: Qual resposta deve ser usada ao consultar a foto de um usuário existente que ainda não possui
  foto atual, distinguindo processamento ativo de falha terminal? → A: Processamento ativo retorna
  HTTP 409; após erro terminal sem foto atual retorna HTTP 404.
- Q: Todas as operações direcionadas a um `usuarioId` inexistente devem retornar HTTP 404,
  incluindo consulta, atualização de nome, exclusão, consulta da foto e upload posterior? → A: Sim,
  todas retornam HTTP 404; processamento inexistente também retorna HTTP 404.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar usuário com foto (Priority: P1)

Como cliente do backend, quero cadastrar um usuário com nome e uma única foto válida para iniciar o
processamento sem aguardar sua conclusão.

**Why this priority**: O cadastro inicia todo o fluxo e não pode deixar usuário sem imagem aceita.

**Independent Test**: Enviar nome e foto válida e confirmar os identificadores e o estado ativo;
repetir sem foto e confirmar que nenhum usuário foi criado.

**Acceptance Scenarios**:

1. **Given** nome válido e uma imagem JPG, JPEG ou PNG dentro do limite, **When** o cadastro é
   solicitado, **Then** o `usuarioId` é gerado automaticamente, um `processamentoId` UUID é gerado,
   a original é preservada, o fluxo assíncrono é aceito e a resposta retorna HTTP 201.
2. **Given** foto ausente, inválida, acima do limite ou mais de uma imagem, **When** o cadastro é
   solicitado, **Then** ele é rejeitado sem deixar usuário ou processamento cadastrado, retornando
   HTTP 400 para foto ausente ou múltipla, HTTP 415 para formato não suportado ou HTTP 413 para
   arquivo acima de 10 MiB.
3. **Given** um cadastro aceito, **When** o usuário é consultado ou atualizado, **Then** seu
   `usuarioId` permanece imutável e não pode ser definido pelo cliente.

---

### User Story 2 - Acompanhar processamento e foto atual (Priority: P1)

Como cliente, quero consultar o processamento e recuperar a foto atual quando concluído.

**Why this priority**: O valor do cadastro depende de uma conclusão observável e do resultado.

**Independent Test**: Criar um upload, consultá-lo até estado terminal e, após `PERSISTIDA`, obter a
foto processada atual.

**Acceptance Scenarios**:

1. **Given** um upload aceito, **When** conclui com sucesso, **Then** percorre `RECEBIDA`,
   `PROCESSANDO`, `PROCESSADA`, `PERSISTINDO` e `PERSISTIDA`, sem regressão.
2. **Given** a processada salva, **When** o resultado é publicado, **Then** contém somente
   referências, identificadores, estado e metadados, nunca bytes.
3. **Given** um resultado consumido com sucesso, **When** a persistência termina, **Then** a imagem
   passa a ser atual somente em `PERSISTIDA`.
4. **Given** um processamento conhecido, **When** consultado, **Then** retorna ao menos seus
   `processamentoId`, `usuarioId` e estado.

---

### User Story 3 - Consultar e manter usuários (Priority: P2)

Como cliente, quero listar, consultar e atualizar o nome de usuários sem alterar seus IDs.

**Why this priority**: Permite administrar usuários já cadastrados.

**Independent Test**: Cadastrar, listar, consultar e renomear um usuário, verificando ID imutável.

**Acceptance Scenarios**:

1. **Given** usuários cadastrados, **When** listados, **Then** cada item apresenta ID, nome e
   referência ou estado da foto atual.
2. **Given** usuário existente, **When** consultado, **Then** seus dados atuais são retornados.
3. **Given** novo nome válido, **When** atualizado, **Then** somente o nome muda e a resposta HTTP
   200 apresenta o usuário atualizado com o mesmo `usuarioId`.

---

### User Story 4 - Substituir foto com continuidade (Priority: P2)

Como cliente, quero atualizar a foto mantendo a anterior até a nova estar totalmente persistida.

**Why this priority**: Evita expor resultado parcial ou indisponibilidade durante a atualização.

**Independent Test**: Enviar nova foto, confirmar novo `processamentoId` UUID, permanência da
anterior e troca apenas em `PERSISTIDA`.

**Acceptance Scenarios**:

1. **Given** usuário sem processamento ativo, **When** nova foto válida é enviada, **Then** novo
   `processamentoId` é criado, o fluxo assíncrono começa e a resposta retorna HTTP 202.
2. **Given** atualização ativa, **When** a foto atual é consultada, **Then** a anterior é retornada.
3. **Given** atualização em `PERSISTIDA`, **When** consultada, **Then** a nova foto é atual.
4. **Given** atualização com erro, **When** consultada, **Then** a foto anterior permanece atual.

---

### User Story 5 - Proteger concorrência e excluir (Priority: P2)

Como operador, quero bloquear uploads e exclusões conflitantes e remover integralmente usuários
quando não houver processamento ativo.

**Why this priority**: Preserva a consistência dos dados e da foto atual.

**Independent Test**: Em cada estado ativo, tentar novo upload e exclusão; depois, em estado
terminal, excluir e verificar a remoção dos dados associados.

**Acceptance Scenarios**:

1. **Given** processamento em `RECEBIDA`, `PROCESSANDO`, `PROCESSADA` ou `PERSISTINDO`, **When** há
   segundo upload ou exclusão, **Then** retorna HTTP 409 sem alterar dados ou criar processamento.
2. **Given** somente estados terminais, **When** nova foto é enviada, **Then** pode ser aceita.
3. **Given** usuário sem processamento ativo, **When** excluído, **Then** usuário, processamentos,
   imagem persistida e objetos originais/processados relacionados são removidos e a resposta é
   HTTP 204 sem conteúdo.
4. **Given** uma exclusão parcialmente executada, **When** algum dado associado permanecer, **Then**
   a operação retorna falha e uma nova tentativa pode retomar a remoção sem efeitos duplicados; o
   sucesso só é informado após a remoção integral.

---

### User Story 6 - Tratar duplicidade e falhas (Priority: P1)

Como operador, quero entregas duplicadas e falhas tratadas sem corrupção ou troca indevida da foto.

**Why this priority**: São condições esperadas no fluxo assíncrono.

**Independent Test**: Reentregar o mesmo resultado, reexecutar o processador após falha de
publicação e simular falhas transitórias, timeout, esgotamento de tentativas, falha definitiva de
processamento e falha definitiva de persistência.

**Acceptance Scenarios**:

1. **Given** resultado duplicado, **When** consumido, **Then** não duplica persistência, registro ou
   promoção da foto, não promove a foto errada e não corrompe estados.
2. **Given** a imagem processada salva e uma falha na publicação do resultado, **When** o mesmo
   `processamentoId` é reexecutado, **Then** o resultado existente pode ser reutilizado e sua
   publicação tentada novamente sem reprocessar desnecessariamente a original ou duplicar efeitos.
3. **Given** falha transitória ou timeout em integração externa, **When** a operação é segura para
   nova tentativa, **Then** podem ocorrer tentativas automáticas limitadas, nunca infinitas.
4. **Given** falha funcional, validação de negócio ou erro claramente não transitório, **When** a
   falha é identificada, **Then** ela não é repetida automaticamente.
5. **Given** falha definitiva de processamento ou tentativas esgotadas, **When** o erro pode ser
   registrado ou comunicado, **Then** termina em
   `ERRO_PROCESSAMENTO`, preserva a original e não substitui a foto atual.
6. **Given** falha definitiva de persistência ou tentativas esgotadas, **When** o erro pode ser
   registrado, **Then** termina em `ERRO_PERSISTENCIA`, preserva a processada, não reprocessa a
   original e não substitui a foto atual.
7. **Given** mensagem de consumo que excedeu o limite configurado de tentativas, **When** não pode
   ser concluída, **Then** o processamento passa a `ERRO_PERSISTENCIA` quando isso puder ser
   registrado, e a mensagem é encaminhada ao Dead Letter Topic mantendo o `processamentoId`.
8. **Given** indisponibilidade prolongada que esgota os mecanismos de recuperação da publicação,
   **When** o resultado não pode ser comunicado, **Then** o sistema registra evidências rastreáveis
   quando possível, sem declarar falsamente a entrega como concluída.

### Edge Cases

- Nome ausente, vazio, só com espaços ou que, após remoção dos espaços externos, tenha menos de 2
  ou mais de 150 caracteres Unicode.
- Foto vazia/corrompida, extensão e conteúdo divergentes, exatamente em 10.485.760 bytes ou um byte
  acima.
- Duas imagens enviadas sob partes distintas ou repetidas.
- Usuário/processamento inexistente ou usuário ainda sem foto atual persistida.
- Evento duplicado concorrente, tardio ou fora de ordem.
- Falha entre salvar a processada e publicar, inclusive indisponibilidade prolongada que esgote as
  tentativas; falha parcial durante exclusão.
- Timeout em integração externa; erro transitório que se recupera dentro do limite; erro funcional
  ou não transitório que não deve ser repetido.
- Mensagem que excede o limite de tentativas e precisa manter rastreabilidade no Dead Letter Topic.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST oferecer somente backend nesta fase, sem frontend, autenticação ou
  autorização.
- **FR-002**: Cadastro MUST exigir nome e exatamente uma foto; rejeições MUST NOT deixar usuário ou
  processamento parcial.
- **FR-003**: O sistema MUST remover espaços externos do nome e, em seguida, MUST exigir de 2 a 150
  caracteres Unicode; nomes vazios ou fora desse intervalo MUST ser rejeitados.
- **FR-004**: Fotos MUST ter conteúdo JPG/JPEG ou PNG, com limite inicial configurável de 10 MiB,
  equivalente a 10.485.760 bytes. Exatamente 10.485.760 bytes MUST ser aceito, qualquer arquivo
  maior MUST ser rejeitado e mais de uma imagem MUST ser rejeitada.
- **FR-005**: `usuarioId` MUST ser automático, imutável, não definível pelo cliente e não reutilizado
  intencionalmente após exclusão.
- **FR-006**: Cada upload aceito MUST gerar UUID `processamentoId` único e rastreável.
- **FR-007**: O aceite inicial MUST ocorrer somente após validação, criação de usuário/processamento,
  preservação da original e entrada em `PROCESSANDO`.
- **FR-008**: A original MUST permanecer inalterada e separada da processada.
- **FR-009**: O processamento MUST ser assíncrono e MUST corrigir a orientação da imagem, limitá-la
  a 1024 por 1024 pixels preservando sua proporção e otimizar seu tamanho. Imagens menores MUST NOT
  ser ampliadas apenas para atingir esse limite.
- **FR-010**: A processada MUST ser salva antes da publicação do resultado; o processador MUST NOT
  acessar dados relacionais.
- **FR-011**: O resultado MUST conter somente referências, IDs, estado e metadados, nunca bytes.
- **FR-012**: O consumidor MUST recuperar e persistir a imagem e seus metadados, promover essa
  imagem como foto atual e somente então concluir em `PERSISTIDA`.
- **FR-013**: O sistema MUST suportar `RECEBIDA`, `PROCESSANDO`, `PROCESSADA`, `PERSISTINDO`,
  `PERSISTIDA`, `ERRO_PROCESSAMENTO` e `ERRO_PERSISTENCIA`.
- **FR-014**: O sucesso MUST seguir a ordem declarada; os quatro primeiros estados são ativos e os
  três últimos são terminais.
- **FR-015**: MUST existir no máximo um processamento ativo por usuário.
- **FR-016**: Segundo upload ou exclusão durante estado ativo MUST retornar HTTP 409 sem mutações.
- **FR-017**: O sistema MUST listar e consultar usuários, atualizar somente o nome, consultar o
  processamento e recuperar a foto atual quando existente.
- **FR-018**: Atualização posterior MUST aplicar as mesmas validações, criar novo
  `processamentoId` UUID e manter a foto anterior até a nova chegar a `PERSISTIDA`; erros MUST
  preservar a anterior. A promoção da nova foto faz parte da conclusão: se falhar, o processamento
  MUST permanecer fora de `PERSISTIDA` e a foto anterior MUST continuar atual.
- **FR-019**: Exclusão permitida MUST remover usuário, processamentos, imagem persistida e todos os
  objetos originais/processados relacionados. A operação MUST ser considerada bem-sucedida somente
  após a remoção integral. Se houver falha parcial, MUST retornar falha enquanto restarem dados
  associados, e novas tentativas MUST retomar a remoção com segurança e sem efeitos duplicados.
- **FR-020**: O consumo MUST ser idempotente perante retry, redelivery e entregas concorrentes. Esses
  eventos MUST NOT duplicar persistência, promover a foto atual mais de uma vez, promover a foto
  errada ou corromper estados.
- **FR-021**: Falha transitória durante o processamento MAY receber tentativas automáticas
  limitadas quando a repetição for segura. Falha definitiva ou esgotamento das tentativas MUST
  preservar a original e, quando o fluxo conseguir registrar ou comunicar o erro, MUST resultar em
  `ERRO_PROCESSAMENTO` sem substituir a foto atual.
- **FR-022**: Falha transitória durante a persistência MAY receber tentativas automáticas limitadas
  quando a repetição for segura. Falha definitiva ou esgotamento das tentativas MUST preservar a
  processada e, quando o fluxo conseguir registrar o erro, MUST resultar em `ERRO_PERSISTENCIA`,
  sem reprocessar a original nem substituir incorretamente a foto atual. Persistir a imagem e seus
  metadados sem conseguir promovê-la como foto atual MUST ser tratado como persistência incompleta:
  a promoção MAY ser repetida com segurança e, se as tentativas forem esgotadas, MUST resultar em
  `ERRO_PERSISTENCIA` quando registrável.
- **FR-023**: Toda operação direcionada a `usuarioId` inexistente MUST retornar HTTP 404, incluindo
  consulta, atualização de nome, exclusão, consulta da foto e upload posterior. Consulta a
  `processamentoId` inexistente MUST retornar HTTP 404. Se existir foto atual `PERSISTIDA`, a consulta
  MUST retorná-la com HTTP 200 mesmo enquanto uma substituição estiver em `PROCESSANDO`. Se houver
  processamento ativo e ainda não existir nenhuma foto atual `PERSISTIDA`, a consulta MUST retornar
  HTTP 409, indicando indisponibilidade temporária. Se o usuário existir, não possuir foto atual e
  todos os seus processamentos estiverem em estado terminal de erro, a consulta MUST retornar HTTP
  404.
- **FR-024**: O contrato MUST incluir cadastro (`POST /api/v1/usuarios`), listagem e consulta,
  atualização de nome, exclusão, foto atual, upload posterior e consulta por `processamentoId`, nos
  caminhos definidos no documento-fonte.
- **FR-025**: A validação oficial MUST cobrir os 13 cenários mínimos Postman definidos na fonte.
- **FR-026**: Falhas transitórias em integrações externas MAY receber tentativas automáticas
  limitadas e, quando adequado ao modo de falha, espera progressiva. Tentativas MUST NOT ser
  infinitas. Falhas funcionais, validações de negócio e erros claramente não transitórios MUST NOT
  ser repetidos. Quantidades, intervalos e política exata pertencem ao planejamento.
- **FR-027**: Integrações externas sujeitas a bloqueio MUST possuir limite de espera adequado. Um
  timeout MAY resultar em nova tentativa somente quando a operação for segura; seu valor exato e a
  combinação com retry pertencem ao planejamento.
- **FR-028**: Componentes de longa duração MAY interromper temporariamente chamadas a uma
  dependência persistentemente indisponível para evitar degradação em cascata. O uso concreto MUST
  ser justificado no planejamento; parâmetros e mecanismos exatos pertencem ao planejamento. Esse
  comportamento MUST NOT ser obrigatório para o `photo-processor`.
- **FR-029**: O `photo-processor` MUST suportar reexecução segura pelo mesmo `processamentoId`. Se a
  processada correspondente já existir após falha de publicação, MUST poder reutilizá-la e tentar
  publicar novamente sem reprocessar desnecessariamente a original e sem efeitos duplicados.
- **FR-030**: Mensagens de consumo Pub/Sub que excederem o limite configurado de tentativas de
  entrega ou processamento MUST ser encaminhadas a um Dead Letter Topic. Quando o
  `processamentoId` estiver presente ou puder ser recuperado, ele MUST permanecer como chave
  principal de rastreabilidade. Mensagens malformadas sem `processamentoId` recuperável MUST NOT
  receber identificador inventado e MUST preservar Pub/Sub message ID, `eventId` quando recuperável,
  atributos disponíveis e payload bruto original para investigação. Quando o estado ainda puder ser
  registrado, o processamento MUST transitar para `ERRO_PERSISTENCIA` antes ou em conjunto com esse
  encaminhamento. Isso MUST NOT substituir idempotência ou registro e tratamento de erros; limites
  e configuração exatos pertencem ao planejamento.
- **FR-031**: Retry, redelivery ou reexecução MUST NOT duplicar persistência, promover uma foto mais
  de uma vez, promover a foto errada, corromper estados ou reprocessar desnecessariamente quando o
  resultado daquele `processamentoId` já existir. Mensagem atrasada ou fora de ordem para um
  processamento já terminal ou que não corresponda mais à foto candidata atual MUST NOT alterar
  estado nem foto atual e MUST permanecer rastreável.
- **FR-032**: Mesmo com recuperação limitada, uma indisponibilidade prolongada MAY impedir a
  comunicação do resultado após esgotar todas as tentativas. O sistema MUST maximizar a recuperação
  por tentativas limitadas, reexecução segura, idempotência e registros rastreáveis, e MUST NOT
  marcar como comunicada uma publicação que não foi confirmada. Enquanto permanecer em estado
  ativo, o processamento MUST continuar bloqueando novos uploads e a exclusão do usuário. Quando a
  falha definitiva puder ser detectada e registrada posteriormente, o processamento MUST transitar
  para `ERRO_PROCESSAMENTO`, tornando-se terminal.
- **FR-033**: Cadastro inicial aceito MUST retornar HTTP 201. Upload posterior de foto aceito MUST
  retornar HTTP 202.
- **FR-034**: Atualização de nome bem-sucedida MUST retornar HTTP 200 com a representação atualizada
  do usuário e seu `usuarioId` inalterado. Exclusão integral bem-sucedida MUST retornar HTTP 204 sem
  conteúdo.
- **FR-035**: Nome inválido, foto obrigatória ausente e múltiplas imagens MUST retornar HTTP 400.
  Formato de imagem não suportado MUST retornar HTTP 415. Arquivo acima de 10 MiB MUST retornar
  HTTP 413. Essas rejeições MUST NOT deixar usuário ou processamento parcial.

### Key Entities *(include if feature involves data)*

- **Usuário**: ID imutável, nome, datas, histórico de processamentos e no máximo uma foto atual.
- **Processamento de Foto**: UUID, usuário, estado, referências, metadados, datas e eventual erro.
- **Imagem Original**: Arquivo aceito, preservado sem alteração e separado do resultado.
- **Imagem Processada**: Resultado separado, elegível como atual somente após `PERSISTIDA`.
- **Resultado de Processamento**: Referências e metadados correlacionados, sem binário.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos cadastros sem foto, com múltiplas fotos, formato inválido ou excesso de
  tamanho são rejeitados sem usuário persistido.
- **SC-002**: 100% dos uploads aceitos recebem IDs únicos e permanecem com processamento
  consultável; quando uma conclusão ou falha puder ser efetivamente registrada ou comunicada, o
  processamento transita para o estado terminal correspondente.
- **SC-003**: 100% dos sucessos preservam a original e só promovem a nova foto em `PERSISTIDA`.
- **SC-004**: 100% dos segundos uploads e exclusões durante estado ativo retornam HTTP 409 sem
  alteração.
- **SC-005**: Três entregas do mesmo resultado geram exatamente uma persistência e no máximo uma
  promoção da foto atual correta, sem corrupção de estado.
- **SC-006**: 100% das mensagens inspecionadas não contêm bytes de imagem.
- **SC-007**: 100% das falhas simuladas que puderem ser registradas ou comunicadas resultam no
  estado correto, preservam os arquivos exigidos e não promovem incorretamente a foto atual; falhas
  que impeçam completamente a comunicação permanecem rastreáveis e nunca são declaradas
  falsamente como concluídas.
- **SC-008**: Os 13 cenários Postman passam de ponta a ponta localmente antes da conclusão da fase.
- **SC-009**: Toda a Fase 1 é validável sem qualquer artefato de frontend.
- **SC-010**: Testes automatizados cobrem ao menos 80% das linhas e passam integralmente.
- **SC-011**: Em 100% das reexecuções testadas do mesmo `processamentoId`, o `photo-processor` não
  duplica efeitos; quando a processada já existe, consegue tentar a publicação posterior sem
  reprocessar a original.
- **SC-012**: Em 100% dos cenários testados de retry ou redelivery, não há persistência duplicada,
  promoção repetida ou incorreta da foto atual nem corrupção de estado.
- **SC-013**: 100% das mensagens de consumo que esgotam o limite configurado são encaminhadas ao
  Dead Letter Topic; permanecem rastreáveis pelo `processamentoId` quando presente ou recuperável e,
  nos demais casos, por Pub/Sub message ID, `eventId` recuperável, atributos disponíveis e payload
  bruto original, sem criação artificial de `processamentoId`.
- **SC-014**: Em 100% dos cenários de timeout e falhas transitórias testados, as tentativas são
  finitas, operações inseguras ou erros não transitórios não são repetidos, e o resultado final é
  observável como sucesso, falha tratada ou comunicação não concluída.

## Assumptions

- O usuário do domínio não é conta autenticada; segurança de acesso está fora da Fase 1.
- O limite é por arquivo: 10 MiB equivalem exatamente a 10.485.760 bytes.
- A preservação de proporção significa que nenhuma dimensão pode superar 1024 pixels e imagens
  menores não são ampliadas.
- O upload retorna após entrar em `PROCESSANDO`, sem aguardar estados posteriores.
- Há vários processamentos históricos, mas apenas um ativo e uma foto atual por usuário.
- Quantidades de tentativas, intervalos, backoff, limites de espera, critérios do mecanismo de
  interrupção temporária e bibliotecas de resiliência são decisões do planejamento.
- Dead Letter Topic complementa, mas não substitui, idempotência, observabilidade ou tratamento de
  erros.
- A especificação não exige Saga, Outbox ou transação distribuída para falha completa de publicação.
- Postman é a validação funcional oficial; sua organização executável pertence ao planejamento.
- Detalhes de componentes, persistência e ambientes pertencem ao plano e devem seguir a constituição.
- Frontend, Vite, TypeScript de interface, HTML de aplicação e componentes visuais estão fora.
