# Delta: Chat System Enhancement

## MODIFIED Requirements

### Requirement: Connect to LLM Server

The system SHALL connect to a llama.cpp server via HTTP API at a configurable base URL, with retry logic for transient failures.

#### Scenario: Successful connection

- WHEN user configures a valid llama.cpp server URL
- THEN system establishes connection and retrieves available models

#### Scenario: Connection failure

- WHEN llama.cpp server is unreachable
- THEN system displays connection error message to user with troubleshooting steps

#### Scenario: Transient connection failure

- WHEN connection fails temporarily
- THEN system retries up to 3 times before showing error

## ADDED Requirements

### Requirement: Request Timeout Handling

The system SHALL handle request timeouts gracefully.

#### Scenario: Request timeout

- WHEN LLM request exceeds timeout (300 seconds)
- THEN system displays timeout error with suggestion to increase timeout

#### Scenario: Partial response on timeout

- WHEN timeout occurs during streaming
- THEN system preserves partial response received so far

## REMOVED Requirements

None
