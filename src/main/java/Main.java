import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import testsmell.AbstractSmell;
import testsmell.ResultsWriter;
import testsmell.TestFile;
import testsmell.TestSmellDetector;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args == null) {
            System.out.println("Please provide the file containing the paths to the collection of test files");
            return;
        }
//        if(!args[0].isEmpty()){
//            File inputFile = new File(args[0]);
//            if(!inputFile.exists() || inputFile.isDirectory()) {
//                System.out.println("Please provide a valid file containing the paths to the collection of test files");
//                return;
//            }
//        }
        if(!args[0].isEmpty()){
            File inputFile = new File(args[0]);
            if(inputFile.exists() && inputFile.isDirectory()) {
                System.out.println("Process Directory: " + inputFile.toString());
            }
        }


        String workingDir = args[0];

        getAllRevisions(workingDir);

//        List<Path> testFiles = findTestFiles(workingDir);

//        detectSmells(workingDir);

        System.out.println("end");
    }

    private static void getAllRevisions(String workingDir) {
        ArrayList<String> allRevisions = new ArrayList<>();
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = null;
        Iterable<RevCommit> log = null;
        Git git = null;
        try {
            repo = builder.setGitDir(new File(workingDir+"/.git")).setMustExist(true).build();
            git = new Git(repo);
            log = null;
            log = git.log().call();
        for (Iterator<RevCommit> iterator = log.iterator(); iterator.hasNext();) {
            RevCommit rev = iterator.next();
            System.out.println(rev.name());
            allRevisions.add(rev.name());
//            git.checkout().setName( rev.name() ).call();
            //            logMessages.add(rev.getFullMessage());
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String rev: allRevisions) {
            try {
                git.checkout().setName( rev ).call();
                System.out.println("Checked Out: "+ rev);
            } catch (GitAPIException e) {
                e.printStackTrace();
            }

        }



    }

    //find all files with an @test annotation
    private static List<Path> findTestFiles(String workingDir) {
        List<Path> filesList = null;
        try {
            filesList = Files.walk(Paths.get(workingDir))
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(f -> {
                        Charset charset = Charset.forName("US-ASCII");
                        try (BufferedReader reader = Files.newBufferedReader(f, charset)) {
                            String line = null;
                            while ((line = reader.readLine()) != null) {
//                                System.out.println(line);
                                if(line.contains("@Test")){
                                    return true;
                                }
                            }
                        } catch (IOException x) {
                            System.err.format("IOException: %s%n", x);
                        }
                    return false;})
                .collect(Collectors.toList());
//                    .forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return filesList;
    }

    private static void detectSmells(String arg) throws IOException {
        TestSmellDetector testSmellDetector = TestSmellDetector.createTestSmellDetector();

        /*
          Read the input file and build the TestFile objects
         */
        BufferedReader in = new BufferedReader(new FileReader(arg));
        String str;

        String[] lineItem;
        TestFile testFile;
        List<TestFile> testFiles = new ArrayList<>();
        while ((str = in.readLine()) != null) {
            // use comma as separator
            lineItem = str.split(",");

            //check if the test file has an associated production file
            if(lineItem.length ==2){
                testFile = new TestFile(lineItem[0], lineItem[1], "");
            }
            else{
                testFile = new TestFile(lineItem[0], lineItem[1], lineItem[2]);
            }

            testFiles.add(testFile);
        }

        /*
          Initialize the output file - Create the output file and add the column names
         */
        ResultsWriter resultsWriter = ResultsWriter.createResultsWriter();
        List<String> columnNames;
        List<String> columnValues;

        columnNames = testSmellDetector.getTestSmellNames();
        columnNames.add(0, "App");
        columnNames.add(1, "Version");
        columnNames.add(2, "TestFilePath");
        columnNames.add(3, "ProductionFilePath");
        columnNames.add(4, "RelativeTestFilePath");
        columnNames.add(5, "RelativeProductionFilePath");

        resultsWriter.writeColumnName(columnNames);

        /*
          Iterate through all test files to detect smells and then write the output
        */
        TestFile tempFile;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date;
        for (TestFile file : testFiles) {
            date = new Date();
            System.out.println(dateFormat.format(date) + " Processing: "+file.getTestFilePath());
            System.out.println("Processing: "+file.getTestFilePath());

            //detect smells
            tempFile = testSmellDetector.detectSmells(file);

            //write output
            columnValues = new ArrayList<>();
            columnValues.add(file.getApp());
            columnValues.add(file.getTagName());
            columnValues.add(file.getTestFilePath());
            columnValues.add(file.getProductionFilePath());
            columnValues.add(file.getRelativeTestFilePath());
            columnValues.add(file.getRelativeProductionFilePath());
            for (AbstractSmell smell : tempFile.getTestSmells()) {
                try {
                    columnValues.add(String.valueOf(smell.getHasSmell()));
                }
                catch (NullPointerException e){
                    columnValues.add("");
                }
            }
            resultsWriter.writeLine(columnValues);
        }
    }


}
