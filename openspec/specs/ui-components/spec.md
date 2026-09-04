# UI Components

## Purpose

Provide the IntelliJ IDEA plugin user interface for chat, settings, and tool windows.

## Requirements

### Requirement: Chat Panel

The system SHALL display a chat interface with message history, input area, and controls.

#### Scenario: Display messages

- WHEN conversation has messages
- THEN system renders user and assistant messages with proper formatting

#### Scenario: Markdown rendering

- WHEN assistant response contains markdown
- THEN system renders markdown with syntax highlighting for code blocks

#### Scenario: Image display

- WHEN user drags image into chat
- THEN system displays image preview and includes in LLM request

### Requirement: Model Selection

The system SHALL provide a dropdown to select available LLM models.

#### Scenario: Populate models

- WHEN plugin starts
- THEN system retrieves available models from llama.cpp server and populates dropdown

#### Scenario: Model change

- WHEN user selects different model
- THEN subsequent requests use newly selected model

### Requirement: Agent Toggle

The system SHALL provide a toggle to enable/disable agent mode.

#### Scenario: Enable agent

- WHEN user checks agent toggle
- THEN system uses AgentExecutor for subsequent requests

#### Scenario: Disable agent

- WHEN user unchecks agent toggle
- THEN system uses direct LLM calls without tool execution

### Requirement: Thinking Panel

The system SHALL display agent thinking process during execution.

#### Scenario: Show thinking

- WHEN agent is executing
- THEN system displays tool calls, results, and thinking text in collapsible panel

#### Scenario: Auto-expand during execution

- WHEN agent starts execution
- THEN thinking panel automatically expands to show progress

#### Scenario: Manual toggle

- WHEN user clicks thinking panel header
- THEN system toggles panel expansion state

### Requirement: Settings UI

The system SHALL provide settings for LLM provider configuration.

#### Scenario: Configure provider

- WHEN user opens settings
- THEN system displays fields for API key, base URL, and model selection

#### Scenario: Save settings

- WHEN user saves settings
- THEN system persists configuration to plugin state

### Requirement: Response Header

The system SHALL display response metadata including model, tokens, and timing.

#### Scenario: Show metadata

- WHEN assistant responds
- THEN system displays model name, token count, and response time

## Implementation Notes

- Uses Swing components with IntelliJ UI DSL
- Thinking panel uses JTextArea with auto-scroll
- Settings stored via PluginStateService
- Tab-based conversation management
