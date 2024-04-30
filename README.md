
# Agent Tracker for MATSim: ReadMe

## Overview

This repository houses Java code to track agent activities and movements within a MATSim simulation environment. It captures and analyzes diverse events such as vehicle entries/exits on links, and person-vehicle interactions, allowing for sophisticated spatial and temporal analysis of agent activities across the network.

## Features

- **Event Handling:** Monitors various events including `LinkEnter`, `LinkLeave`, `PersonEntersVehicle`, `PersonLeavesVehicle`, `ActivityStart`, `ActivityEnd`, `PersonDeparture`, and `PersonArrival`.
- **Agent Tracking:** Correlates vehicles with their occupants and traces their routes through the simulation network.
- **Spatial Analysis:** Evaluates agents' locations on the network grid for detailed spatial analysis.
- **Flexible Outputs:** Offers mechanisms to export processed data into JSON and CSV formats for further examination.

## Installation

Install [Matsim](https://github.com/matsim-org/matsim-example-project) first and then move this file to the project directory. After the simulation for one scenario is finished, you can use this code to generate the output files. 


## Usage

### Configuration

Begin by initializing an instance of `AgentTracker` with appropriate parameters to filter agents based on regex patterns and whether to ignore public transport (PT) agents:

```java
String[] ignoreRegex = new String[]{"example_regex"}; // Define regex patterns to ignore
boolean ignorePtAgents = true; // Set to true to ignore PT drivers
AgentTracker tracker = new AgentTracker(ignoreRegex, ignorePtAgents);
```

### Running the Simulation

Load your network and events data, then process it through the event handler:

```java
String eventsFilePath = "path_to_events_file.xml"; // Path to your MATSim events file
String networkFilePath = "path_to_network_file.xml"; // Path to your MATSim network file
tracker.iterateEvents(eventsFilePath, networkFilePath);
```

### Data Extraction

To export the processed data to files:

```java
// JSON format for tiles with time and person aggregation
tracker.savePersonsTimeListToJson("output_path.json");

// CSV format detailing person movements and time spent in tiles
tracker.savePersonToTileMapCsv("output_path.csv");
```

## Integration

To integrate this tracking setup with a MATSim simulation, follow the steps outlined in the `AgentTracker` class's `main` method. This method demonstrates initializing the tracker with specific filters, running the simulation, and exporting the results.


## License

This project is released under the GPL-2.0 License. See the [License](https://github.com/amirbabaei97/Matsim-Agent-Tracker/blob/main/LICENSE) file for more details.

---
