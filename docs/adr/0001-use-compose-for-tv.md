# Use Kotlin and Jetpack Compose for TV

Tilevio Launcher targets only Android TV and Google TV devices running Android 10 or later, with low resource use as a permanent design rule. We will use native Kotlin with Jetpack Compose for TV because it provides direct Android integration and remote-friendly focus behavior while keeping the interface straightforward to build and maintain. We will avoid unnecessary dependencies, animations, resident services, and background work; Compose must not become a reason to add features or processing that the product does not need.

## Considered Options

- **Flutter**: rejected because its cross-platform engine adds runtime weight without providing a cross-platform benefit for this Android-only project.
- **Native Android Views**: potentially more frugal in a carefully hand-written implementation, but rejected in favor of Compose for TV's simpler UI development and built-in TV interaction support. This is a deliberate development-simplicity trade-off, not a claim that Compose is always the lightest option.
