# OpenSpec SDD

## Purpose

Implement Specification-Driven Development (SDD) workflow within the plugin, allowing users to manage specifications, create changes, and merge delta specs into main specs.

## Requirements

### Requirement: Spec File Format

The system SHALL parse spec files in OpenSpec-compatible markdown format.

#### Scenario: Parse valid spec

- WHEN system reads a valid spec.md file
- THEN system extracts title, purpose, requirements, and scenarios

#### Scenario: Parse spec with code fences

- WHEN spec contains fenced code blocks
- THEN code fences are masked during parsing to avoid false section matches

#### Scenario: Invalid spec format

- WHEN spec file has missing required sections
- THEN parser returns error indicating missing sections

### Requirement: Delta Spec Format

The system SHALL parse delta spec files with ADDED/MODIFIED/REMOVED/RENAMED operations.

#### Scenario: Parse delta with additions

- WHEN delta contains "## ADDED Requirements"
- THEN parser extracts new requirements to add

#### Scenario: Parse delta with removals

- WHEN delta contains "## REMOVED Requirements"
- THEN parser extracts requirements to remove

#### Scenario: Parse delta with modifications

- WHEN delta contains "## MODIFIED Requirements"
- THEN parser extracts requirements to modify with updated content

#### Scenario: Parse delta with renames

- WHEN delta contains "## RENAMED Requirements"
- THEN parser extracts FROM/TO rename pairs

### Requirement: Delta Merge

The system SHALL merge delta operations into main spec files.

#### Scenario: Merge additions

- WHEN delta adds new requirements
- THEN merged spec includes original requirements plus new ones

#### Scenario: Merge removals

- WHEN delta removes requirements
- THEN merged spec excludes removed requirements

#### Scenario: Merge modifications

- WHEN delta modifies requirements
- THEN merged spec replaces original content with modified content

#### Scenario: Merge renames

- WHEN delta renames requirements
- THEN merged spec updates requirement names accordingly

#### Scenario: Near-miss typo detection

- WHEN removed requirement name is similar to existing requirement
- THEN system warns about possible typo and suggests correct name

#### Scenario: Retirement detection

- WHEN all requirements are removed from a spec
- THEN system marks spec as retired and deletes spec file

### Requirement: Change Management

The system SHALL manage changes with proposal, tasks, and delta specs.

#### Scenario: Create change

- WHEN user creates a new change
- THEN system scaffolds proposal.md, tasks.md, and specs/ directory

#### Scenario: List changes

- WHEN user lists active changes
- THEN system shows change names with task completion progress

#### Scenario: Archive change

- WHEN user archives a completed change
- THEN system merges delta specs into main specs and moves change to archive/

### Requirement: Spec Validation

The system SHALL validate spec and delta files for correctness.

#### Scenario: Validate spec

- WHEN system validates a spec file
- THEN system checks for required sections and valid structure

#### Scenario: Validate delta

- WHEN system validates a delta file
- THEN system checks for cross-section conflicts and duplicate operations

### Requirement: Artifact Graph

The system SHALL determine build order for artifacts using topological sort.

#### Scenario: Linear dependencies

- WHEN artifacts have linear dependency chain
- THEN system returns correct build order

#### Scenario: Circular dependency

- WHEN artifacts have circular dependency
- THEN system detects cycle and returns error

## Implementation Notes

- Spec files stored in: `openspec/specs/{domain}/spec.md`
- Delta files stored in: `openspec/changes/{name}/specs/{domain}/spec.md`
- Archive stored in: `openspec/archive/{name}/`
- Merge order: RENAMED → REMOVED → MODIFIED → ADDED
- Uses Kahn's algorithm for topological sort
