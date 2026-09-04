# Chat System

## Purpose

Provide a chat interface for interacting with local LLM models via llama.cpp server, supporting both streaming and non-streaming responses.

## Requirements

### Requirement: Connect to LLM Server

The system SHALL connect to a llama.cpp server via HTTP API at a configurable base URL.

#### Scenario: Successful connection

- WHEN user configures a valid llama.cpp server URL
- THEN system establishes connection and retrieves available models

#### Scenario: Connection failure

- WHEN llama.cpp server is unreachable
- THEN system displays connection error message to user

### Requirement: Stream Responses

The system SHALL support real-time streaming of LLM responses token-by-token.

#### Scenario: Streaming response

- WHEN user sends a message
- THEN system displays response tokens as they arrive from the server

#### Scenario: Stop streaming

- WHEN user clicks stop button during streaming
- THEN system interrupts the current stream and preserves partial response

### Requirement: Model Selection

The system SHALL allow users to select from available models on the llama.cpp server.

#### Scenario: List models

- WHEN system connects to llama.cpp server
- THEN system retrieves and displays list of available models in dropdown

#### Scenario: Switch model

- WHEN user selects a different model from dropdown
- THEN subsequent requests use the newly selected model

### Requirement: Non-Streaming Mode

The system SHALL support non-streaming mode for agent tool-calling workflows.

#### Scenario: Non-streaming request

- WHEN agent executes tool-calling loop
- THEN system waits for complete response before processing

## Implementation Notes

- Uses `LlamaChatStreamClient` for raw HTTP SSE streaming
- Uses `OpenAiChatModel` for non-streaming agent requests
- Base URL auto-appends `/v1` if not present
- Default timeout: 300 seconds
