package br.com.autonomiccs.cloudTraces.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.autonomiccs.cloudTraces.exceptions.GoogleTracesToCloudTracesException;

public class ProcessLogFileResults {

    private final static String STRING_BEFORE_VALUE_OF_MEMORY_STD_BEFORE_MANAGEMENT = "before management; memory STD [";
    private final static Pattern PATTERN_MEMORY_STD_BEFORE_MANAGEMENT = Pattern
            .compile(Pattern.quote(STRING_BEFORE_VALUE_OF_MEMORY_STD_BEFORE_MANAGEMENT) + "(.*?)" + Pattern.quote("Gib]"));

    private final static String STRING_BEFORE_VALUE_OF_CPU_STD = "cpu STD [";
    private final static Pattern PATTERN_CPU_STD = Pattern.compile(Pattern.quote(STRING_BEFORE_VALUE_OF_CPU_STD) + "(.*?)" + Pattern.quote("Ghz]"));

    private final static String STRING_BEFORE_VALUE_OF_MEMORY_STD_AFTER_MANAGEMENT = "after management; memory STD [";
    private final static Pattern PATTERN_MEMORY_STD_AFTER_MANAGEMENT = Pattern
            .compile(Pattern.quote(STRING_BEFORE_VALUE_OF_MEMORY_STD_AFTER_MANAGEMENT) + "(.*?)" + Pattern.quote("Gib]"));

    private final static String STRING_BEFORE_PROCESING_TIME = "total processing time [";
    private final static Pattern PATTERN_PROCESING_TIME = Pattern.compile(Pattern.quote(STRING_BEFORE_PROCESING_TIME) + "(.*?)" + Pattern.quote("]"));

    private final static String STRING_BEFORE_SELECTED_HEURISTIC = "selected map index [";
    private final static Pattern PATTERN_SELECTED_HEURISTIC = Pattern.compile(Pattern.quote(STRING_BEFORE_SELECTED_HEURISTIC) + "(.*?)" + Pattern.quote("]"));

    private final static String STRING_BEFORE_NUMBER_OF_HOSTS = "number of hosts [";
    private final static Pattern PATTERN_NUMBER_OF_HOSTS = Pattern.compile(Pattern.quote(STRING_BEFORE_NUMBER_OF_HOSTS) + "(.*?)" + Pattern.quote("]"));

    private final static String STRING_BEFORE_NUMBER_OF_VMS = "number of VMs [";
    private final static Pattern PATTERN_NUMBER_OF_VMS = Pattern.compile(Pattern.quote(STRING_BEFORE_NUMBER_OF_VMS) + "(.*?)" + Pattern.quote("]"));

    private final static String STRING_BEFORE_NUMBER_OF_MIGRATIONS = "#migrations [";
    private final static Pattern PATTERN_MIGRATION = Pattern.compile(Pattern.quote(STRING_BEFORE_NUMBER_OF_MIGRATIONS) + "(.*?)" + Pattern.quote("]"));

    private final static Pattern PATTERN_MEMORY_STD = Pattern.compile(Pattern.quote("] memory STD [") + "(.*?)" + Pattern.quote("Gib]"));
    private final static Pattern PATTERN_CLUSTER_ID = Pattern.compile(Pattern.quote("Cluster [cluster-") + "(.*?)" + Pattern.quote("] memory STD "));

    private static double wightedMemoryStdSum = 0;
    private static double weightedCpuStd = 0;
    private static long cloudMemory = 0;
    private static long cloudCpu = 0;
    private static int numberOfClusters = 0;
    private static List<Double> clustersMemoryStd = new ArrayList<>();
    private static List<Double> clustersCpuStd = new ArrayList<>();

    private static List<Double> clustersMemory = new ArrayList<>();
    private static List<Double> clustersCpu = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        validateInputFile(args);

        //        String simulatedResultsLogFile = "cloud-traces.log";
        String simulatedResultsLogFile = args[0];

        PrintWriter outputFile = new PrintWriter("simulationResultsToAnalyse.txt");
        BufferedReader bufferedReader = new BufferedReader(new FileReader(simulatedResultsLogFile));

        List<String> linesToWrite = new ArrayList<>();

        mainLog(bufferedReader, linesToWrite);
        //        bestResults(bufferedReader, linesToWrite);
        //        allocatedLog(bufferedReader, linesToWrite);
        //        standardDeviationsLog(bufferedReader, linesToWrite);

        writeLines(outputFile, linesToWrite);

        bufferedReader.close();
        outputFile.close();
        System.out.println("Finished!");
    }

    private static void mainLog(BufferedReader bufferedReader, List<String> linesToWrite) throws IOException {
        String lineToRead;
        String processingTime = "", migrations = "", cpuBefore = "", cpuAfter = "", memoryBefore = "", memoryAfter = "", numberOfHosts = "", numberOfVms = "",
                selectedHeuristic = "";
        linesToWrite.add("Migrations CpuStdBefore CpuStdAfter MemoryStdBefore MemoryStdAfter ProcessingTime NumberOfHosts NumberOfVms SelectedHeuristic");
        while ((lineToRead = bufferedReader.readLine()) != null) {
            Matcher matcherMigration = PATTERN_MIGRATION.matcher(lineToRead);
            Matcher matcherCpuStd = PATTERN_CPU_STD.matcher(lineToRead);
            Matcher matcherMemoryStdBefore = PATTERN_MEMORY_STD_BEFORE_MANAGEMENT.matcher(lineToRead);
            Matcher matcherMemoryStdAfter = PATTERN_MEMORY_STD_AFTER_MANAGEMENT.matcher(lineToRead);
            Matcher matcherProcessingTime = PATTERN_PROCESING_TIME.matcher(lineToRead);
            Matcher matcherSelectedHeuristic = PATTERN_SELECTED_HEURISTIC.matcher(lineToRead);
            Matcher matcherNumberOfHosts = PATTERN_NUMBER_OF_HOSTS.matcher(lineToRead);
            Matcher matcherNumberOfVms = PATTERN_NUMBER_OF_VMS.matcher(lineToRead);

            if (matcherMigration.find()) {
                String numberOfMigrations = matcherMigration.group(1);
                if (numberOfMigrations.equals("0")) {
                    continue;
                }
                migrations = numberOfMigrations;

                matcherProcessingTime.find();
                processingTime = matcherProcessingTime.group(1);
            } else if (matcherSelectedHeuristic.find()) {
                selectedHeuristic = matcherSelectedHeuristic.group(1);

                matcherNumberOfHosts.find();
                numberOfHosts = matcherNumberOfHosts.group(1);

                matcherNumberOfVms.find();
                numberOfVms = matcherNumberOfVms.group(1);
            } else if (matcherMemoryStdBefore.find()) {
                memoryBefore = matcherMemoryStdBefore.group(1);

                matcherCpuStd.find();
                cpuBefore = matcherCpuStd.group(1);
            } else if (matcherMemoryStdAfter.find()) {
                memoryAfter = matcherMemoryStdAfter.group(1);

                matcherCpuStd.find();
                cpuAfter = matcherCpuStd.group(1);

                addLineToListOfLinesToWrite(linesToWrite, migrations, cpuBefore, cpuAfter, memoryBefore, memoryAfter, processingTime, numberOfHosts, numberOfVms,
                        selectedHeuristic);
            }
        }
    }

    private static void allocatedLog(BufferedReader bufferedReader, List<String> linesToWrite) throws IOException {
        String lineToRead;
        String time = "90000.00";
        String clusters = "1";
        String currentTime;
        linesToWrite.add("TIME CPU_STD_MEAN MEMORY_STD_MEAN");

        while ((lineToRead = bufferedReader.readLine()) != null) {
            Matcher matcherCpuStd = PATTERN_CPU_STD.matcher(lineToRead);
            Matcher matcherMemoryStd = PATTERN_MEMORY_STD.matcher(lineToRead);
            Matcher matcherClusterId = PATTERN_CLUSTER_ID.matcher(lineToRead);

            Pattern PATTERN_TIME = Pattern.compile(Pattern.quote("time [") + "(.*?)" + Pattern.quote("]"));
            Matcher matcherTime = PATTERN_TIME.matcher(lineToRead);

            Pattern PATTERN_CLOUD_MEMORY = Pattern.compile(Pattern.quote("Cloud configuration: Cloud id [Google data traces], total memory [") + "(.*?)" + Pattern.quote("MB]"));
            Pattern PATTERN_CLOUD_CPU = Pattern.compile(Pattern.quote("], total cpu [") + "(.*?)" + Pattern.quote("Mhz]"));
            Pattern PATTERN_CLUSTER_CONFIGURATION = Pattern.compile(Pattern.quote("Cluster configuration at time "));
            Pattern CLUSTER_MEMORY = Pattern.compile(Pattern.quote("total memory [") + "(.*?)" + Pattern.quote("MB]"));
            Pattern CLUSTER_CPU = Pattern.compile(Pattern.quote("total cpu [") + "(.*?)" + Pattern.quote("Mhz]"));

            Matcher matcherCloudMemory = PATTERN_CLOUD_MEMORY.matcher(lineToRead);
            Matcher matcherCloudCpu = PATTERN_CLOUD_CPU.matcher(lineToRead);
            Matcher matcherClusterConfiguration = PATTERN_CLUSTER_CONFIGURATION.matcher(lineToRead);
            Matcher matcherClusterCpu = CLUSTER_MEMORY.matcher(lineToRead);
            Matcher matcherClusterMemory = CLUSTER_CPU.matcher(lineToRead);

            if (matcherCloudMemory.find()) {
                cloudMemory = Long.parseLong(matcherCloudMemory.group(1));
                matcherCloudCpu.find();
                cloudCpu = Long.parseLong(matcherCloudCpu.group(1));
            }

            if (matcherClusterConfiguration.find()) {
                matcherClusterCpu.find();
                matcherClusterMemory.find();

                clustersCpu.add(Double.parseDouble(matcherClusterCpu.group(1)));
                clustersMemory.add(Double.parseDouble(matcherClusterMemory.group(1)));
            }

            matcherTime.find();
            if (matcherClusterId.find()) {
                currentTime = matcherTime.group(1);
                currentTime = currentTime.replace(",", ".");
                matcherMemoryStd.find();
                matcherCpuStd.find();
                if (time.equals(currentTime)) {
                    addClusterMemoryAndCpuStd(clusters, matcherCpuStd, matcherMemoryStd, matcherClusterId);
                } else {
                    for(int i = 0; i < numberOfClusters; i++) {
                        weightedCpuStd += (clustersCpuStd.get(i) * (clustersCpu.get(i) / cloudCpu));
                        wightedMemoryStdSum += (clustersMemoryStd.get(i) * (clustersMemory.get(i) / cloudMemory));
                    }
                    linesToWrite.add(String.format("%s %s %s", time, weightedCpuStd / numberOfClusters, wightedMemoryStdSum / numberOfClusters));

                    weightedCpuStd = 0;
                    wightedMemoryStdSum = 0;
                    numberOfClusters = 0;
                    clustersMemoryStd = new ArrayList<>();
                    clustersCpuStd = new ArrayList<>();
                    clustersMemory = new ArrayList<>();
                    clustersCpu = new ArrayList<>();

                    time = currentTime;
                    addClusterMemoryAndCpuStd(clusters, matcherCpuStd, matcherMemoryStd, matcherClusterId);
                }
            }
        }
    }

    private static void addClusterMemoryAndCpuStd(String clusters, Matcher matcherCpuStd, Matcher matcherMemoryStd,
            Matcher matcherClusterId) {
        String memoryString = matcherMemoryStd.group(1);
        memoryString = memoryString.replace(",", ".");
        wightedMemoryStdSum += Double.valueOf(memoryString);

        String cpuString = matcherCpuStd.group(1);
        cpuString = cpuString.replace(",", ".");
        weightedCpuStd += Double.valueOf(cpuString);

        clustersMemoryStd.add(Double.valueOf(memoryString));
        clustersCpuStd.add(Double.valueOf(cpuString));

        clusters = matcherClusterId.group(1);
        numberOfClusters = Integer.valueOf(clusters);
    }

    private static void bestResults(BufferedReader bufferedReader, List<String> linesToWrite) throws IOException {
        String lineToRead;
        Pattern PATTERN_BEST_RESULTS_COUNT = Pattern.compile(Pattern.quote("MinimumResults=[") + "(.*?)" + Pattern.quote("]"));

        linesToWrite.add(
                "COSINE_MEAN COSINE_MEDIAN MEAN_ALPHA1 MEAN_ALPHA2 MEAN_ALPHA10 MEDIAN_ALPHA1 MEDIAN_ALPHA2 MEDIAN_ALPHA10");

        while ((lineToRead = bufferedReader.readLine()) != null) {
            Matcher matcherBestResults = PATTERN_BEST_RESULTS_COUNT.matcher(lineToRead);

            if (matcherBestResults.find()) {
                String bestRestults = matcherBestResults.group(1);
                linesToWrite.add(bestRestults);
            }
        }
    }

    private static void standardDeviationsLog(BufferedReader bufferedReader, List<String> linesToWrite) throws IOException {
        String lineToRead;
        Pattern PATTERN_STANDARD_DEVIATIONS = Pattern.compile(Pattern.quote("StandardDeviations=[") + "(.*?)" + Pattern.quote("]"));

        linesToWrite.add("COSINE_MEAN COSINE_MEDIAN MEAN_ALPHA1 MEAN_ALPHA2 MEAN_ALPHA10 MEDIAN_ALPHA1 MEDIAN_ALPHA2 MEDIAN_ALPHA10");

        while ((lineToRead = bufferedReader.readLine()) != null) {
            Matcher matcherStandardDeviations = PATTERN_STANDARD_DEVIATIONS.matcher(lineToRead);

            if (matcherStandardDeviations.find()) {
                String bestRestults = matcherStandardDeviations.group(1);
                linesToWrite.add(bestRestults);
            }
        }
    }

    private static void addLineToListOfLinesToWrite(List<String> linesToWrite, String migrations, String cpuBefore, String cpuAfter, String memoryBefore, String memoryAfter,
            String processingTime, String numberOfHosts, String numberOfVms, String selectedHeuristic) {
        if (!migrations.equals("") && !cpuBefore.equals("") && !cpuAfter.equals("") && !memoryBefore.equals("") && !memoryAfter.equals("")) {
            linesToWrite.add(String.format("%s %s %s %s %s %s %s %s %s", migrations, cpuBefore, cpuAfter, memoryBefore, memoryAfter, processingTime, numberOfHosts, numberOfVms,
                    selectedHeuristic));
        } else {
            linesToWrite.add(String.format("Falied to write this line; migrations=[%s], cpuBefore=[%s], cpuAfter=[%s], memoryBefore=[%s], memoryAfter=[%s]", migrations, cpuBefore,
                    cpuAfter, memoryBefore, memoryAfter));
        }
    }

    private static void writeLines(PrintWriter outputFile, List<String> linesToWrite) {
        for (String line : linesToWrite) {
            outputFile.println(line);
        }
    }

    private static void validateInputFile(String[] args) {
        if (args.length != 1) {
            throw new GoogleTracesToCloudTracesException("You should inform the full qualified path to the cloud traces data set.");
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            throw new GoogleTracesToCloudTracesException(String.format("File [%s] does not exist.", args[0]));
        }
        if (!file.canRead()) {
            throw new GoogleTracesToCloudTracesException(String.format("Cannot read file [%s] .", args[0]));
        }
    }

}

