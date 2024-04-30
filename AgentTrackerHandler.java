package org.matsim.project;

import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.ActivityEndEvent;
// import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
// import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.vehicles.Vehicle;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.apache.logging.log4j.core.util.SystemClock;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;

import org.matsim.api.core.v01.population.Person;

import java.util.regex.Pattern;

import java.util.function.DoubleFunction;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.matsim.project.utils.BresenhamLine;

import org.matsim.core.utils.collections.Tuple;
import org.matsim.api.core.v01.network.Node;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedWriter;

// Define a custom handler for LinkEnterEvent.
class AgentTrackerEventHandler
        implements LinkEnterEventHandler, LinkLeaveEventHandler, PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, ActivityStartEventHandler, ActivityEndEventHandler,
        PersonDepartureEventHandler, PersonArrivalEventHandler {

    private final Network network;
    private final Pattern[] ignorePatterns;

    // vehicle to list of persons map 
    private final Map<Id<Vehicle>, List<Id<Person>>> vehicleToPersonMap = new HashMap<>();

    // Link to LinkCoords map
    private final Map<Id<Link>, LinkCoords> linkCoordsMap = new HashMap<>();

    // Link to Grids map
    Map<String, Tuple<Tuple<Integer, Integer>, Double>[]> linkToGridsWithRatio = new HashMap<>();

    // person to [tile, time] map
    private final Map<Id<Person>, List<Tuple<String, Tuple<Integer, Integer>>>> personToTileTimeMap = new HashMap<>();

    // Person to list of PersonEvent map for xfunction and yfunction
    public final Map<Id<Person>, List<PersonEvent>> personToEventMap = new HashMap<>();

    // Person to ActivityStartEvent map
    public final Map<Id<Person>, ActivityStartEvent> lastActivityStartEventMap = new HashMap<>();

    public AgentTrackerEventHandler(Network network, Pattern[] ignorePatterns) {
        this.network = network;
        this.ignorePatterns = ignorePatterns;
    }

    class LinkCoords {
        public Coord from;
        public Coord to;
        public Coord mid;

        public LinkCoords(Coord from, Coord to) {
            this.from = from;
            this.to = to;
            this.mid = new Coord((from.getX() + to.getX()) / 2, (from.getY() + to.getY()) / 2);
        }
    }

    private List<Tuple<String, Tuple<Integer, Integer>>> getOrCreateTileTimeList(Id<Person> personId) {
        if (!personToTileTimeMap.containsKey(personId)) {
            personToTileTimeMap.put(personId, new ArrayList<>());
        }
        return personToTileTimeMap.get(personId);
    }

    public Tuple<Tuple<Integer, Integer>, Double>[] calculateGridCellsWithRatio(double x1, double y1, double x2,
            double y2) {
        // shift
        int x_shift = (int) (x1 / 100) * 100;
        int y_shift = (int) (y1 / 100) * 100;
        x1 -= x_shift;
        x2 -= x_shift;
        y1 -= y_shift;
        y2 -= y_shift;

        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

        // Calculate the grid cell coordinates
        Set<BresenhamLine.Pair> tiles = BresenhamLine.bresenhamLine((int) x1, (int) y1, (int) x2, (int) y2, 100, 1);
        double m = ((double) y2 - y1) / ((double) x2 - x1); // slope (y2-y1)/(x2-x1)
        double c = y1 - m * x1; // intercept y = mx + c

        Tuple<Tuple<Integer, Integer>, Double>[] gridCellsWithRatio = new Tuple[tiles.size()];
        int i = 0;
        for (BresenhamLine.Pair tile : tiles) {
            Integer xGrid = (int) Math.floor(tile.x * 100);
            Integer yGrid = (int) Math.floor(tile.y * 100);
            Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);
            double dist = BresenhamLine.segmentInsideSquare(m, c, xGrid, yGrid, 100, (int) x1, (int) y1, (int) x2,
                    (int) y2);
            // shift back
            gridCell = new Tuple<>(gridCell.getFirst() + x_shift, gridCell.getSecond() + y_shift);
            gridCellsWithRatio[i] = new Tuple(gridCell, dist / length);
            i++;
        }

        return gridCellsWithRatio;
    }

    public Tuple<Tuple<Integer, Integer>, Double>[] getOrCreateGridsWithRatio(String linkId) {
        // Get the link's coordinates
        if (linkToGridsWithRatio.containsKey(linkId)) {
            return linkToGridsWithRatio.get(linkId);
        }

        Link link = network.getLinks().get(Id.create(linkId, Link.class));
        Node fromNode = link.getFromNode();
        Node toNode = link.getToNode();

        double x1 = fromNode.getCoord().getX();
        double y1 = fromNode.getCoord().getY();
        double x2 = toNode.getCoord().getX();
        double y2 = toNode.getCoord().getY();

        return calculateGridCellsWithRatio(x1, y1, x2, y2);
    }

    class PersonEvent {
        public String type;
        public Id<Vehicle> vehicleId;
        public Id<Link> linkId;
        public Coord coord;
        public double time;
        public String xFunction;
        public String yFunction;

        public PersonEvent(String type, Id<Vehicle> vehicleId, Id<Link> linkId, Coord coord,
                double time, String xFunction, String yFunction) {
            this.type = type;
            this.vehicleId = vehicleId;
            this.linkId = linkId;
            this.coord = coord;
            this.time = time;
            this.xFunction = xFunction;
            this.yFunction = yFunction;
        }
    }

    private boolean shouldIgnorePerson(String personId) {
        for (Pattern pattern : ignorePatterns) {
            if (pattern.matcher(personId).matches()) {
                return true;
            }
        }
        return false;
    }


    public LinkCoords getLinkCoords(Id<Link> linkId) {
        if (!linkCoordsMap.containsKey(linkId)) {
            Link link = network.getLinks().get(linkId);
            linkCoordsMap.put(linkId, new LinkCoords(link.getFromNode().getCoord(), link.getToNode().getCoord()));
        }
        return linkCoordsMap.get(linkId);
    }

    @Override
    public void reset(int iteration) {
        System.out.println("Resetting...");
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        // check if vehicleToPersonMap contains vehicleId and it is not empty
        if (!vehicleToPersonMap.containsKey(vehicleId) || vehicleToPersonMap.get(vehicleId).isEmpty()) {
            return;
        }
        List<Id<Person>> personIds = vehicleToPersonMap.get(vehicleId);
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).from;
        double time = event.getTime();
        for (Id<Person> personId : personIds) {
            PersonEvent personEvent = new PersonEvent(event.getEventType(), vehicleId, linkId, coord, time, "", "");
            personToEventMap.get(personId).add(personEvent);
        }
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        // check if vehicleToPersonMap contains vehicleId and it is not empty
        if (!vehicleToPersonMap.containsKey(vehicleId) || vehicleToPersonMap.get(vehicleId).isEmpty()) {
            return;
        }
        List<Id<Person>> personIds = vehicleToPersonMap.get(vehicleId);
        Id<Link> linkId = event.getLinkId();
        Tuple<Tuple<Integer, Integer>, Double>[] gridsWithRatio = getOrCreateGridsWithRatio(linkId.toString());

        int grids_len = gridsWithRatio.length;
        double time = event.getTime();
        double time_passed = 0;
        int time_interval = 0;
        for (int i = 0; i < grids_len; i++) {
            Tuple<Tuple<Integer, Integer>, Double> gridWithRatio = gridsWithRatio[i];
            Tuple<Integer, Integer> grid = gridWithRatio.getFirst();
            Double ratio = gridWithRatio.getSecond();
            for (Id<Person> personId : personIds) {
                PersonEvent lastPersonEvent = personToEventMap.get(personId)
                        .get(personToEventMap.get(personId).size() - 1);
                time_interval = (int) (event.getTime() - lastPersonEvent.time);
                int tile_time = (int) (ratio * time_interval);
                List<Tuple<String, Tuple<Integer, Integer>>> tileTimeLilst = getOrCreateTileTimeList(personId);
                String tile = String.format("%s,%s", grid.getFirst(), grid.getSecond());
                int time_start = (int) (time + time_passed);
                tileTimeLilst.add(new Tuple(tile, new Tuple((int) time_start, (int) (time_start + tile_time))));
            }
            time_passed += ratio * time_interval;
        }


    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Person> personId = event.getPersonId();
        if (!vehicleToPersonMap.containsKey(vehicleId)) {
            vehicleToPersonMap.put(vehicleId, new ArrayList<>());
        }
        vehicleToPersonMap.get(vehicleId).add(personId);
        if (!personToEventMap.containsKey(personId)) {
            personToEventMap.put(personId, new ArrayList<>());
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Person> personId = event.getPersonId();
        if (!vehicleToPersonMap.containsKey(vehicleId)) {
            return;
        }
        vehicleToPersonMap.get(vehicleId).remove(personId);
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        lastActivityStartEventMap.put(event.getPersonId(), event);
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Person> personId = event.getPersonId();
        double startTime = 0;
        if (!personToEventMap.containsKey(personId)) {
            personToEventMap.put(personId, new ArrayList<>());
        } else {
            startTime = lastActivityStartEventMap.get(personId).getTime();
            // drop the last ActivityStartEvent
            lastActivityStartEventMap.remove(personId);
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        double time = event.getTime();
        List<Tuple<String, Tuple<Integer, Integer>>> tileTimeLilst = getOrCreateTileTimeList(personId);
        tileTimeLilst
                .add(new Tuple(String.format("%s,%s", (int) coord.getX() / 100 * 100, (int) coord.getY() / 100 * 100),
                        new Tuple((int) startTime, (int) (time))));

        PersonEvent personEvent = new PersonEvent(event.getActType(), null, linkId, coord, startTime, "", "");
        personToEventMap.get(personId).add(personEvent);
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (event.getLegMode().equals("walk") || event.getLegMode().equals("bicycle")) {
            return; // Skip handling walk and bicycle events
        }
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        double time = event.getTime();
        PersonEvent personEvent = new PersonEvent("Walk_Bike", null, linkId, coord, time, "", "");
        personToEventMap.get(event.getPersonId()).add(personEvent);
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if (event.getLegMode().equals("walk") || event.getLegMode().equals("bicycle")) {
            return; // Skip handling walk and bicycle events
        }
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        PersonEvent lastPersonEvent = personToEventMap.get(event.getPersonId())
                .get(personToEventMap.get(event.getPersonId()).size() - 1);
        // if type is not PersonDepartureEvent, then ignore
        if (!lastPersonEvent.type.equals("Walk_Bike")) {
            return;
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        // Tuple<Tuple<Integer, Integer>, Double>[] gridsWithRatio =
        // calculateGridCellsWithRatio(
        // lastPersonEvent.coord.getX(),
        // lastPersonEvent.coord.getY(), coord.getX(), coord.getY());
        Tuple<Tuple<Integer, Integer>, Double>[] gridsWithRatio = getOrCreateGridsWithRatio(linkId.toString());

        int grids_len = gridsWithRatio.length;
        List<Tuple<String, Tuple<Integer, Integer>>> tileTimeLilst = getOrCreateTileTimeList(event.getPersonId());

        double time = event.getTime();
        double time_passed = 0;
        int time_interval = (int) (time - lastPersonEvent.time);
        for (int i = 0; i < grids_len; i++) {
            Tuple<Tuple<Integer, Integer>, Double> gridWithRatio = gridsWithRatio[i];
            Tuple<Integer, Integer> grid = gridWithRatio.getFirst();
            Double ratio = gridWithRatio.getSecond();
            int tile_time = (int) (ratio * time_interval);
            String tile = String.format("%s,%s", grid.getFirst(), grid.getSecond());
            int time_start = (int) (time + time_passed);
            tileTimeLilst.add(new Tuple(tile, new Tuple((int) time_start, (int) (time_start + tile_time))));
            time_passed += ratio * time_interval;
        }
    }

    public void savePersonsTimeListToJson(String filePath) {
        for (Map.Entry<Id<Person>, ActivityStartEvent> entry : lastActivityStartEventMap.entrySet()) {
            Id<Person> personId = entry.getKey();
            ActivityStartEvent activityStartEvent = entry.getValue();
            Id<Link> linkId = activityStartEvent.getLinkId();
            Coord coord = getLinkCoords(linkId).mid;
            double time = activityStartEvent.getTime();
            List<Tuple<String, Tuple<Integer, Integer>>> tileTimeLilst = getOrCreateTileTimeList(personId);
            tileTimeLilst.add(
                    new Tuple(String.format("%s,%s", (int) coord.getX() / 100 * 100, (int) coord.getY() / 100 * 100),
                            new Tuple((int) time, (int) (time + 10 * 3600))));
        }
        try {
            Map<String, Map<String, Map<String, Double>>> transformedMap = new HashMap<>();
            
            System.out.println("personToTileTimeMap size: " + personToTileTimeMap.size());
            int count = 0;
            for (Map.Entry<Id<Person>, List<Tuple<String, Tuple<Integer, Integer>>>> entry : personToTileTimeMap
                    .entrySet()) {
                if (++count % 1000 == 0) {
                    System.out.println("count: " + count);
                }
                for (Tuple<String, Tuple<Integer, Integer>> tuple : entry.getValue()) {
                    String tile = tuple.getFirst();
                    Tuple<Integer, Integer> time = tuple.getSecond();
                    int startHour = time.getFirst() / 3600;
                    int endHour = time.getSecond() / 3600;

                    for (int hour = startHour; hour <= endHour; hour++) {
                        int startSecond = Math.max(time.getFirst(), hour * 3600);
                        int endSecond = Math.min(time.getSecond(), (hour + 1) * 3600);
                        double population = (endSecond - startSecond) / 3600.0;

                        Map<String, Map<String, Double>> tileMap = transformedMap.getOrDefault(tile, new HashMap<>());
                        Map<String, Double> hourMap = tileMap.getOrDefault(String.valueOf(hour), new HashMap<>());
                        hourMap.put("population", hourMap.getOrDefault("population", 0.0) + population);
                        tileMap.put(String.valueOf(hour), hourMap);
                        transformedMap.put(tile, tileMap);
                    }
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), transformedMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void savePersonToTileMapCsv(String filePath) {
        try {
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("PersonId", "X", "Y", "StartTime", "EndTime"));

            for (Map.Entry<Id<Person>, ActivityStartEvent> entry : lastActivityStartEventMap.entrySet()) {
                Id<Person> personId = entry.getKey();
                ActivityStartEvent activityStartEvent = entry.getValue();
                Id<Link> linkId = activityStartEvent.getLinkId();
                Coord coord = getLinkCoords(linkId).mid;
                double time = activityStartEvent.getTime();
                List<Tuple<String, Tuple<Integer, Integer>>> tileTimeList = getOrCreateTileTimeList(personId);
                tileTimeList.add(
                        new Tuple(String.format("%s,%s", (int) coord.getX() / 100 * 100, (int) coord.getY() / 100 * 100),
                                new Tuple((int) time, (int) (time + 10 * 3600))));

                for (Tuple<String, Tuple<Integer, Integer>> tuple : tileTimeList) {
                    csvPrinter.printRecord(personId, tuple.getFirst().split(",")[0], tuple.getFirst().split(",")[1], tuple.getSecond().getFirst(), tuple.getSecond().getSecond());
                }
            }

            csvPrinter.flush();
            csvPrinter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}