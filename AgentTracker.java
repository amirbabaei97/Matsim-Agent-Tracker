package org.matsim.project;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.vehicles.Vehicle;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.api.core.v01.Coord;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class AgentTracker {

    private final EventsManager eventsManager;
    private final Pattern[] ignorePatterns;
    private Network network;

    public AgentTracker(String[] ignoreRegex, boolean ignorePtAgents) {
        this.eventsManager = EventsUtils.createEventsManager();

        // Add pt_.* to ignoreRegex if ignorePtAgents is true and it is not already for the PT drivers
        if (ignorePtAgents) {
            Set<String> ignoreSet = new HashSet<>(Arrays.asList(ignoreRegex));
            ignoreSet.add("pt_.*");
            ignoreRegex = ignoreSet.toArray(new String[ignoreSet.size()]);
        }

        this.ignorePatterns = new Pattern[ignoreRegex.length];
        for (int i = 0; i < ignoreRegex.length; i++) {
            ignorePatterns[i] = Pattern.compile(ignoreRegex[i]);
        }
    }

    public void iterateEvents(String filePath, String networkPath) {
        
        network = NetworkUtils.readNetwork(networkPath);

        // Register your event handler.
        AgentTrackerEventHandler handler = new AgentTrackerEventHandler(network, ignorePatterns);
        eventsManager.addHandler(handler);
        // Create an events reader and connect it with the manager.
        MatsimEventsReader reader = new MatsimEventsReader(eventsManager);

        // Now read the file.
        reader.readFile(filePath);

        System.out.println("Done");

        // handler.saveToCSV("scenarios\\ASIMOW\\base\\agent_tracker.csv");
        // handler.SavePopulationPertilePerhourJson("scenarios\\ASIMOW\\5km_streets\\agent_tracker_tiles.json");
        handler.savePersonsTimeListToJson("C:\\Users\\ReLUT_PC\\Desktop\\Matsim-ASIMOW\\scenarios\\ASIMOW\\base\\agent_tracker_tiles.json");
        // handler.savePersonToTileMap("scenarios\\ASIMOW\\base\\agent_tracker_details.json");
        handler.savePersonToTileMapCsv("C:\\Users\\ReLUT_PC\\Desktop\\Matsim-ASIMOW\\scenarios\\ASIMOW\\base\\agent_tracker_details.csv");
    }

    public static void main(String[] args) {
        // Your events file path.
        String filePath = "C:\\Users\\ReLUT_PC\\Desktop\\Matsim-ASIMOW\\scenarios\\ASIMOW\\base\\ASIMOW.output_events.xml.gz";
        String networkPath = "C:\\Users\\ReLUT_PC\\Desktop\\Matsim-ASIMOW\\scenarios\\ASIMOW\\base\\ASIMOW.output_network.xml.gz";

        // Create the iterator and iterate through the events.
        AgentTracker iterator = new AgentTracker(new String[] { "back.*" }, true);
        iterator.iterateEvents(filePath, networkPath);

    }
}
