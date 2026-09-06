<!--
Sync Impact Report
- Version change: template (unratified) -> 1.0.0
- Modified principles:
  - template placeholder -> I. Backend First (NON-NEGOTIABLE)
  - template placeholder -> II. Standardized Stack
  - template placeholder -> III. Feature-Oriented, Pragmatic Design
  - template placeholder -> IV. Automated and Functional Quality Gates
  - template placeholder -> V. Asynchronous Architecture and Clear Boundaries
  - Added VI. MVP Simplicity
  - Added VII. Local-First, Cloud-Targeted Environments
- Added sections:
  - Permanent Technology Constraints
  - Engineering Workflow and Quality Gates
- Removed sections: none (template placeholders replaced)
- Follow-up TODOs: none
-->
# Async Photo Processing Platform Constitution

## Core Principles

### I. Backend First (NON-NEGOTIABLE)
Phase 1 MUST implement only the backend. It MUST NOT create frontend applications, application
pages, Vite projects, interface TypeScript, or UI components. The backend MUST be complete and
validated by automated tests and the official Postman functional suite before any frontend work
begins. This sequencing keeps the first delivery focused on proving the service contracts,
asynchronous workflow, persistence, and infrastructure independently of a user interface.

### II. Standardized Stack
Backend components MUST use Java 25 and Spring Boot 4.0.0. Builds MUST use Maven 3.9.12 through
the committed Maven Wrapper; contributors MUST NOT depend on a different globally installed Maven
version. MySQL 8.4 LTS MUST be used locally, Flyway MUST own database schema migrations, and Cloud
SQL for MySQL MUST remain the target relational database on GCP. A stack or version change requires
a constitution amendment with documented compatibility and migration impact.

### III. Feature-Oriented, Pragmatic Design
Spring Boot services MUST use Package by Feature. Internal request flow MUST preserve the
Controller -> Service -> Repository boundary; controllers MUST NOT access repositories directly.
SOLID principles MUST be applied where they measurably improve cohesion, coupling, testability,
legibility, or maintenance, but MUST NOT justify speculative layers or abstractions. Java Records
SHOULD represent immutable requests, responses, events, and integration contracts when compatible
with their semantics; they MUST NOT be forced onto mutable JPA entities or behavior-rich domain
objects. Generic DTO organization, unnecessary interfaces, and complete Clean or Hexagonal
Architecture MUST NOT be introduced without an explicit demonstrated need.

### IV. Automated and Functional Quality Gates
Unit tests MUST use JUnit 5 and Mockito. Relevant integration boundaries, especially database and
cross-component behavior, MUST use Testcontainers where it provides production-like confidence.
JaCoCo MUST enforce an initial minimum of 80% line coverage for the backend; coverage does not
replace meaningful assertions or scenario coverage. The Postman collection MUST be the official
functional validation of the Phase 1 API. A backend change is not complete while applicable tests
fail, the coverage gate is unmet, or required Postman scenarios have not been validated.

### V. Asynchronous Architecture and Clear Boundaries
`photo-api` and `photo-consumer` MUST be independent Spring Boot microservices.
`photo-processor` MUST be a Cloud Run Function and MUST NOT access MySQL. It MUST read the original
image from object storage, write the processed image to object storage, and publish the result to
Pub/Sub. Pub/Sub messages MUST carry references, identifiers, status, and metadata only; image
bytes MUST NOT be placed on the message bus. `photo-consumer` MUST process deliveries idempotently
so duplicate messages cannot duplicate persistence or corrupt state. These boundaries keep binary
payloads in object storage and make asynchronous ownership explicit.

### VI. MVP Simplicity
The MVP MUST NOT implement authentication, authorization, Spring Security, JWT, or OAuth2.
Automatic Retry, Circuit Breaker, and Timeout mechanisms MUST NOT be introduced as resilience
strategies in this phase. Full Clean Architecture or Hexagonal Architecture, speculative adapters,
factories, interfaces, layers, technologies, and abstractions MUST NOT be added without a concrete,
current requirement. The smallest design that satisfies the specification and quality gates MUST
be preferred; any added complexity MUST be justified in the feature plan.

### VII. Local-First, Cloud-Targeted Environments
The complete asynchronous flow MUST run and be validated locally before GCP deployment is treated
as ready. The local environment MUST use Docker Compose with MySQL 8.4 LTS, `fake-gcs-server`, the
Pub/Sub Emulator, and Functions Framework, together with the backend components. The GCP target
MUST use Cloud Storage, Pub/Sub, Cloud Run Functions, and Cloud SQL for MySQL. Differences between
local and GCP integrations MUST be handled through external configuration rather than divergent
business logic, and secrets MUST remain outside version control.

## Permanent Technology Constraints

- The required runtime and build baseline is Java 25, Spring Boot 4.0.0, and Maven 3.9.12 via
  Maven Wrapper.
- Relational schema changes MUST be versioned as Flyway migrations and tested against MySQL 8.4
  LTS behavior.
- Object data belongs in object storage; Pub/Sub is limited to references and metadata.
- Environment-specific endpoints, credentials, bucket names, topics, and database connections MUST
  be supplied through configuration.
- Logs for asynchronous operations MUST include the processing correlation identifier defined by
  the applicable feature contract.
- Business rules, API resources, state transitions, limits, and feature-specific acceptance
  scenarios MUST remain in feature specifications, not in this constitution.

## Engineering Workflow and Quality Gates

1. Every feature specification and plan MUST pass a constitution check before implementation.
2. Plans MUST identify component ownership, asynchronous boundaries, persistence changes, Flyway
   migrations, relevant test levels, and local validation steps.
3. Implementation MUST preserve Package by Feature and the Controller -> Service -> Repository
   dependency direction. Deviations require explicit justification in the plan and review.
4. Changes MUST be verified with applicable unit and Testcontainers integration tests through the
   Maven Wrapper, followed by the JaCoCo 80% line-coverage gate.
5. Phase 1 completion MUST include an end-to-end local run and successful validation of required
   Postman scenarios. Frontend work is prohibited until this gate passes.
6. Code review MUST reject unrequested technologies, speculative abstractions, image bytes in
   Pub/Sub, database access from `photo-processor`, and non-idempotent consumer behavior.

## Governance

This constitution is the highest engineering-governance authority for the project. Feature
specifications define business behavior but MUST comply with it; where they conflict, this
constitution prevails. Amendments MUST be proposed as an explicit constitution change, document
the rationale and migration or compatibility impact, update the Sync Impact Report, and receive
maintainer approval before dependent work proceeds.

Versions follow semantic versioning: MAJOR for removal or incompatible redefinition of governance,
MINOR for a new principle or materially expanded mandate, and PATCH for non-semantic clarification.
The ratification date remains the original adoption date; the last-amended date changes whenever
governance content changes. Each specification, implementation plan, task set, and code review MUST
verify compliance. Any temporary exception MUST be documented with scope, rationale, owner, and an
expiry or removal condition; silent exceptions are prohibited.

**Version**: 1.0.0 | **Ratified**: 2026-09-06 | **Last Amended**: 2026-09-06
