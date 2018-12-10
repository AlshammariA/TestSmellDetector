import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import testsmell.AbstractSmell;
import testsmell.ResultsWriter;
import testsmell.TestFile;
import testsmell.TestSmellDetector;
import testsmell.SmellyElement;

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

        /*
          Initialize the output file - Create the output file and add the column names
         */
        TestSmellDetector testSmellDetector = TestSmellDetector.createTestSmellDetector();
        ResultsWriter resultsWriter = ResultsWriter.createResultsWriter();
        List<String> columnNames;

        columnNames = testSmellDetector.getTestSmellNames();
        columnNames.add(0, "App");
        //columnNames.add(1, "Version");
        columnNames.add(1, "TestFilePath");
        columnNames.add(2, "ProductionFilePath");
        columnNames.add(3, "version");
        //columnNames.add(4, "RelativeTestFilePath");
        //columnNames.add(5, "RelativeProductionFilePath");
        columnNames.add(4, "MethodName");

        resultsWriter.writeColumnName(columnNames);
        ArrayList<String> allRevisions = getAllRevisions(workingDir);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = builder.setGitDir(new File(workingDir+"/.git")).setMustExist(true).build();
        Git git = new Git(repo);
        for(String rev : allRevisions) {
            try {
                git.checkout().setName( rev ).call();
                System.out.println("Checked Out: "+ rev);
                List<Path> testFiles = findTestFiles(workingDir);
                for (Path path : testFiles) {
                    System.out.println("path: " + path.toString());
                    detectSmells(path.toString(), rev, testSmellDetector, resultsWriter);
                }

            } catch (GitAPIException e) {
                e.printStackTrace();
            }
        }
        System.out.println("end");
    }

    private static ArrayList<String>  getAllRevisions(String workingDir) {
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

        return allRevisions;
        /*for (String rev: allRevisions) {
            try {
                git.checkout().setName( rev ).call();
                System.out.println("Checked Out: "+ rev);
            } catch (GitAPIException e) {
                e.printStackTrace();
            }

        }*/
    }

    //find all files with an @test annotation
    private static List<Path> findTestFiles(String workingDir) {
        List<Path> filesList = null;
        try {
            filesList = Files.walk(Paths.get(workingDir))
                    .filter(p -> p.toString().endsWith("Test.java"))
                    /*.filter(f -> {
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
                    return false;})*/
                .collect(Collectors.toList());
//                    .forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return filesList;
    }

    private static void detectSmells(String path, String version, TestSmellDetector testSmellDetector, ResultsWriter resultsWriter) throws IOException {
        //TestSmellDetector testSmellDetector = TestSmellDetector.createTestSmellDetector();
        List<String> columnValues;

        TestFile testFile = new TestFile("Ninja", path, "");
        /*
          Iterate through all test files to detect smells and then write the output
        */
        TestFile tempFile;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date;
        date = new Date();
        System.out.println(dateFormat.format(date) + " Processing: "+testFile.getTestFilePath());
        System.out.println("Processing: "+testFile.getTestFilePath());

        //detect smells
        try {
            tempFile = testSmellDetector.detectSmells(testFile);
        } catch(Exception e) {
            return;
        }
        List<AbstractSmell> smells = tempFile.getTestSmells();
        List<SmellyElement> elements = smells.get(0).getSmellyElements();
        for (SmellyElement el : elements) {
            columnValues = new ArrayList<>();
            columnValues.add(testFile.getApp());
            //columnValues.add(file.getTagName());
            columnValues.add(testFile.getTestFilePath());
            columnValues.add(testFile.getProductionFilePath());
            columnValues.add(version);
            //columnValues.add(file.getRelativeTestFilePath());
            //columnValues.add(file.getRelativeProductionFilePath());
            columnValues.add(el.getElementName());
            for(AbstractSmell smell : smells) {
                if(smell == null) {
                    columnValues.add("");
                    continue;
                }
                List<SmellyElement> elements2 = smell.getSmellyElements();
                if(elements2.size() == 0) {
                    try{
                        columnValues.add(String.valueOf(false));
                    }
                    catch (NullPointerException e) {
                        columnValues.add("");
                    }
                    continue;
                }
                for(SmellyElement el2 : elements2) {
                    if(el2.getElementName().equals(el.getElementName())) {
                        try{
                            columnValues.add(String.valueOf(el2.getHasSmell()));
                        }
                        catch (NullPointerException e) {
                            columnValues.add("");
                        }
                        break;
                    }
                }
            }
            resultsWriter.writeLine(columnValues);
        }
    }
}