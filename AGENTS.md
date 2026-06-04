# Repository Guidelines

- Code Style: Follow the existing code style and conventions. Use consistent formatting and naming.
- Git: Do not run git commands in automation; maintainers handle commits.
- Principle: Adhere strictly to DRY; prefer shared utilities to duplication.
- No source files with more than 350 lines; split into focused modules if needed. This limit does not apply to docs, data files, generated files, lockfiles, or binary assets.
- When you clean up old code or remove features, also remove related helpers, submodules, and tests in order to keep the codebase clean and DRY.

# Minecraft Plugin Structure to follow
BiomeMap : https://github.com/Sukikui/BiomeMap
Other interesting codebase (client mod): https://github.com/Sukikui/PlayerCoordsAPI