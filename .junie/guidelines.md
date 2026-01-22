# Project Overview: Monorepo Profiles

## Introduction
Monorepo Profiles is an IntelliJ Platform Plugin designed to help developers manage profiles in monorepo projects. It simplifies working with large monorepo codebases by allowing users to define profiles that include specific paths in their project, making navigation and development more efficient.

## Key Features
- Automatic detection of profile configuration files in the project
- Profile application by including specified paths and excluding all others
- User interface for selecting and toggling profiles on/off
- Seamless integration with IntelliJ-based IDEs

## Project Structure
The project follows a modular architecture with the following components:

- **model**: Contains data classes for profiles and profile configurations
- **services**: Implements core services for profile management and path exclusion
- **listeners**: Handles events related to profile configuration changes
- **ui**: Provides user interface components for profile selection and management
- **helpers**: Utility classes for notifications and other helper functions
- **settings**: Manages plugin settings and state persistence

## Usage
1. Create a `monorepo-profiles.yaml` file in your project with the following structure:
   ```yaml
   name: "Profile Name"
   includedPaths:
     - "path/to/include"
   ```
2. The plugin automatically detects and applies the profile
3. To select and toggle profiles, use the "Select Monorepo Profiles" action from the Tools menu or press Ctrl+Alt+P

## Development Guidelines
- Follow Kotlin coding conventions
- Update documentation when making significant changes
