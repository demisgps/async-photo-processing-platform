<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.1.0
- Modified principles:
  - IV. Automated and Functional Quality Gates -> expanded with resilience failure-mode tests
  - V. Asynchronous Architecture and Clear Boundaries -> expanded with safe re-execution, resume
    after publication failure, and DLQ requirements
  - VI. MVP Simplicity -> replaced blanket resilience prohibition with selective, pragmatic use
- Added sections: none
- Removed sections: none
- Other updated guidance:
  - Permanent Technology Constraints: resilience observability and processing correlation
  - Engineering Workflow and Quality Gates: failure-mode justification, retry safety, DLQ, and
    anti-cascade review requirements
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
fail, the coverage gate is unmet, or required Postman scenarios have not been validated. Tests for
external integrations and asynchronous flows MUST cover applicable transient failures, timeout,
bounded retry, duplicate delivery, safe re-execution, exhausted delivery routed to a dead-letter
destination, and recovery without duplicate side effects.

### V. Asynchronous Architecture and Clear Boundaries
`photo-api` and `photo-consumer` MUST be independent Spring Boot microservices.
`photo-processor` MUST be a Cloud Run Function and MUST NOT access MySQL. It MUST read the original
image from object storage, write the processed image to object storage, and publish the result to
Pub/Sub. Pub/Sub messages MUST carry references, identifiers, status, and metadata only; image
bytes MUST NOT be placed on the message bus. `photo-consumer` MUST process deliveries idempotently
so retry or duplicate delivery cannot duplicate persistence, promote the wrong current photo,
repeat a promotion, or corrupt state. `photo-processor` MUST support safe re-execution for the same
processing identifier. If the processed object already exists, a new attempt MUST be able to resume
at result publication without unnecessarily processing the original again. In particular, saving
the processed image followed by a publication failure MUST be recoverable safely.

Pub/Sub flows MUST define a Dead Letter Topic or Queue strategy for messages that exceed the bounded
delivery or processing attempt limit. Dead-lettered messages MUST remain traceable by processing
identifier. A DLQ does not replace error handling, idempotency, or observability. These boundaries
keep binary payloads in object storage and make asynchronous ownership and recovery explicit.

### VI. MVP Simplicity
The MVP MUST NOT implement authentication, authorization, Spring Security, JWT, or OAuth2.
Resilience mechanisms MUST be selected for concrete failure modes and MUST NOT be added uniformly,
speculatively, or merely to increase architectural complexity.

Retry MUST be limited to transient failures in external integrations, MUST have a maximum attempt
count, and MUST NOT permit infinite attempts. Exponential backoff MUST be used when the documented
failure mode and dependency behavior make it appropriate. Functional errors, business validation
failures, and clearly non-transient failures MUST NOT be retried automatically. External calls MUST
have an appropriate time limit when they could otherwise block indefinitely. A timeout MAY be
combined with retry only when repeating the operation is safe and bounded.

Circuit Breaker MUST be considered selectively for long-running components when repeated calls to
an unavailable dependency can cause degradation or cascading failure. Its use MUST be justified in
the feature plan and MUST NOT be applied automatically to every integration. The stateless,
short-lived `photo-processor` MUST NOT receive a Circuit Breaker solely for architectural
uniformity. Retry, timeout, Circuit Breaker, and DLQ policies MUST be designed together to avoid
retry storms and cascading effects.

Full Clean Architecture or Hexagonal Architecture, speculative adapters, factories, interfaces,
layers, libraries, technologies, and abstractions MUST NOT be added without a concrete, current
requirement. Exact libraries and retry, timeout, and Circuit Breaker parameters belong in feature
planning, not this constitution. The smallest design that satisfies the specification, resilience
needs, and quality gates MUST be preferred; any added complexity MUST be justified in the plan.

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
  the applicable feature contract. Logs for retries, timeouts, Circuit Breaker state changes, and
  dead-letter routing MUST include that identifier whenever it is available, enabling end-to-end
  tracing.
- Business rules, API resources, state transitions, limits, and feature-specific acceptance
  scenarios MUST remain in feature specifications, not in this constitution.

## Engineering Workflow and Quality Gates

1. Every feature specification and plan MUST pass a constitution check before implementation.
2. Plans MUST identify component ownership, asynchronous boundaries, persistence changes, Flyway
   migrations, relevant test levels, and local validation steps. For every proposed retry, timeout,
   Circuit Breaker, or DLQ, the plan MUST name the concrete failure mode, safe-operation conditions,
   bounded-attempt behavior, and applicable observability. Exact libraries and parameters MUST be
   decided there rather than elevated into permanent governance.
3. Implementation MUST preserve Package by Feature and the Controller -> Service -> Repository
   dependency direction. Deviations require explicit justification in the plan and review.
4. Changes MUST be verified with applicable unit and Testcontainers integration tests through the
   Maven Wrapper, followed by the JaCoCo 80% line-coverage gate.
5. Phase 1 completion MUST include an end-to-end local run and successful validation of required
   Postman scenarios. Frontend work is prohibited until this gate passes.
6. Code review MUST reject unrequested technologies, speculative abstractions, image bytes in
   Pub/Sub, database access from `photo-processor`, non-idempotent event handling, unbounded retry,
   indiscriminate retry, and resilience combinations that can create retry storms or cascading
   failure.
7. Integration validation MUST prove safe duplicate delivery and processor re-execution, including
   the case where the processed object exists but result publication previously failed. Applicable
   Pub/Sub flows MUST also prove traceable routing after delivery attempts are exhausted.

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

**Version**: 1.1.0 | **Ratified**: 2026-09-06 | **Last Amended**: 2026-09-07
