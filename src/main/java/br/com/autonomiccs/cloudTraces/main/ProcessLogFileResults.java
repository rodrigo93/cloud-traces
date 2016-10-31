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

    private final static String FIRST_LINE = "Migrations CpuStdBefore CpuStdAfter MemoryStdBefore MemoryStdAfter ProcessingTime NumberOfHosts NumberOfVms SelectedHeuristic";

    public static void main(String[] args) throws IOException {
        //        validateInputFile(args);

        String simulatedResultsLogFile = "cloud-traces.log";

        PrintWriter outputFile = new PrintWriter("simulationResultsToAnalyse.txt");
        outputFile.println(FIRST_LINE);

        BufferedReader bufferedReader = new BufferedReader(new FileReader(simulatedResultsLogFile));

        List<String> linesToWrite = new ArrayList<>();
        String lineToRead;
        String processingTime = "", migrations = "", cpuBefore = "", cpuAfter = "", memoryBefore = "", memoryAfter = "", numberOfHosts = "", numberOfVms = "",
                selectedHeuristic = "";
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

        writeLines(outputFile, linesToWrite);

        bufferedReader.close();
        outputFile.close();
        System.out.println("Finished!");
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

