# Conversation Management

## Purpose

Manage chat conversations with persistence, history, and context window management.

## Requirements

### Requirement: Conversation Storage

The system SHALL persist conversations to local JSON files.

#### Scenario: Save conversation

- WHEN user sends or receives a message
- THEN conversation is saved to `.idea/myplugin-conversations.json`

#### Scenario: Load conversation

- WHEN user opens a conversation tab
- THEN system loads conversation history from storage

#### Scenario: Multiple conversations

- WHEN user creates multiple chat tabs
- THEN each conversation is stored independently with unique ID

### Requirement: Message History

The system SHALL maintain ordered message history per conversation.

#### Scenario: Add user message

- WHEN user sends a message
- THEN system appends USER message to history with timestamp

#### Scenario: Add assistant message

- WHEN LLM responds
- THEN system appends ASSISTANT message to history

#### Scenario: Clear history

- WHEN user clicks clear button
- THEN system removes all messages from current conversation

### Requirement: Context Window Management

The system SHALL limit conversation history sent to LLM to prevent context overflow.

#### Scenario: History within limit

- WHEN conversation has fewer than 20 messages
- THEN all messages are sent to LLM

#### Scenario: History exceeds limit

- WHEN conversation has more than 20 messages
- THEN only last 20 messages are sent to LLM

### Requirement: Token Calculation

The system SHALL calculate token usage and costs for cloud providers.

#### Scenario: Count tokens

- WHEN user sends a message
- THEN system displays estimated token count

#### Scenario: Cost estimation

- WHEN using cloud provider
- THEN system displays estimated cost based on token usage

### Requirement: Conversation Tabs

The system SHALL support multiple conversation tabs with independent state.

#### Scenario: New tab

- WHEN user clicks "New Chat" button
- THEN system creates new conversation tab with empty history

#### Scenario: Switch tab

- WHEN user clicks on a conversation tab
- THEN system loads and displays that conversation's messages

#### Scenario: Close tab

- WHEN user closes a conversation tab
- THEN system removes tab and preserves conversation in storage

## Implementation Notes

- Storage format: JSON with LocalDateTime adapter
- Project-scoped service (one per project)
- Auto-save on each message
- Default context window: 20 messages
