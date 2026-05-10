# Minecraft Server Stats Manager

A Java Swing desktop app for tracking Minecraft servers, members, UUIDs, and Minecraft player stats JSON.

## Features

- Server select screen with **No Servers Created** empty state
- Add and delete servers
- Add members to each server with member name and UUID
- Member pages show saved UUID and parsed stats
- Input Stats screen accepts pasted `.json` player stat data
- Saving new JSON updates/replaces the member stats shown on the member page
- Server page has an **Update Member Stats** button
- Bulk update screen supports drag-and-drop or file picker selection for `.json` files
- Bulk updater reads the UUID from each `.json` filename and updates the matching member automatically
- UUID matching works with hyphenated or non-hyphenated UUIDs
- Data saves locally between runs

## Bulk Stat Update

Minecraft player stat files are usually named after the player UUID, for example:

```text
12345678-1234-1234-1234-123456789abc.json
```

On a server page, click **Update Member Stats**, then drag one or more `.json` files into the drop area. The app will compare the filename UUID to the members saved on that server and update the matching member stats.

## Run

On macOS/Linux:

```bash
./gradlew run
```

On Windows:

```bat
gradlew.bat run
```

## Build a jar

```bash
./gradlew jar
```

The jar will be created in:

```text
build/libs/mc-server-stats-manager-0.1.0-alpha.jar
```

## Saved Data Location

The app stores data in your user folder:

```text
~/.mc-server-stats-manager/data.json
```

Deleting that file resets the app.
