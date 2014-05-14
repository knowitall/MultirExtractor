package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.FigerAndNERTypeSignaturePERPERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation.SentDocName;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.ExtractionAnnotation;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorConcatFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorIndepFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorWithFIGER;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.sententialextraction.DocumentExtractor;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.EvaluationUtils;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.ModelUtils;

/**
 * This class is designed to output two files for populating the Information Omnivore database:
 *
 *  1. Sentences file, containing lines of tab-separated fields:
 *     This file is written only if it does not already exist, as the file is identical independent of which class writes it,
 *     but the data structures needed to generate it are required for writing the second file even if the first is not written.
 *
 *       1. documentID: string identifying document name, e.g. NYT_ENG_20010101.0001 (with suffix “.LDC*” removed)
 *       2. startSentence: document-relative offset of start of sentence
 *       3. endSentence: document-relative offset of end of sentence
 *       4. sentenceString: text of sentence
 *
 *     Note that a "sentence" is a unique combination of documentID string, offsets (startSentence/endSentence), and text (sentenceString).
 *     Identical strings of text appearing at different places in the same document or in different documents are different "Sentences".
 *
 *  2. ExtractionsVotes file, containing lines of tab-separated fields:
 *
 *       1. extractorName                    // “MultiR”
 *       2. score                            // total feature weight (double float in exponential notation)
 *       3. documentID                       // a unique document identifier, e.g. NYT_ENG_20010101.0001 (with suffix “.LDC*” removed)
 *       4. relationName                     // e.g. "/people/person/nationality"
 *       5. relationKB                       // “FreeBase”
 *       6. startArg1                        // document-relative byte offset of start of first argument
 *       7. endArg1                          // document-relative byte offset of end of first argument
 *       8. arg1String                       // text of first argument
 *       9. startArg2                        // document-relative byte offset of start of second argument
 *      10. endArg2                          // document-relative byte offset of end of second argument
 *      11. arg2String                       // text of second argument
 *      12. startSentence                    // document-relative byte offset of the start of the sentence
 *      13. endSentence                      // document-relative byte offset of the end of the sentence
 *      14. extractorExperimentID            // identifier for the experiment
 *      15. extractionDate                   // date for the experiment in format YYYY-MM-DD
 *      16. workerID                         // a unique identifier for each worker
 *      17. vote                             // real number. 0.0 = false, 1.0 = true
 *      18. voteRelationExperID              // a unique name, so that we can distinguish different voting experiments
 *      19. voteRelationDate                 // date for the vote in format YYYY-MM-DD
 *      20. sentenceString                   // text of the full sentence
 *
 *  Arguments to the main function when invoking class DBPopulatePartitionedModelFiles:
 *  Args are zero-indexed here to correspond to arguments to "arguments" function for accessing each argument.  Actual args should NOT be quoted as illustrated here.
 *
 *       0. Input Corpus (ie, "/projects/WebWare6/Multir/MultirSystem/files/derbyCorpus/FullCorpus")
 *       1. Input Manual Annotations file (ie, "/scratch/manualAnnotations.csv")
 *       2. Input Partitioned Relations file (ie, "/projects/WebWare6/Multir/MultirSystem/files/targetRelations/partitionRelations.txt")
 *       3. "train" to run Training subset of corpus or "test" to run Test subset.
 *       4. Input file containing list of test documents (ie, "/projects/WebWare6/Multir/MultirSystem/files/testDocuments")
 *       5. Experiment ID (ie, "Partitioned_Model")
 *       6. Experiment Date in format YYYY-MM-DD
 *       7. Worker (voter) ID (ie, "Siegfried")
 *       8. Vote Experiment ID (ie, "Third_Attempt")
 *       9. Date of Vote in format YYYY-MM-DD
 *      10. Output Sentences file (ie, "/scratch/MultiR_01/Sentences")
 *      11. Output ExtractionsVotes file (ie, "/scratch/MultiR_01/GenPartExtractionsVotes")
 *      Keyword arg for NER argument identification class (ie, "-ai NERArgumentIdentification")
 *      Keyword args for Sentential Instance Generation classes (ie, "-siglist FigerAndNERTypeSignatureORGPERSententialInstanceGeneration FigerAndNERTypeSignaturePERPERSententialInstanceGeneration FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration")
 *      Keyword args for directories containing MILDoc input files (ie, "-files /scratch/MultiR_01/Generalized_ORGPER_MILDocs /scratch/MultiR_01/Generalized_PERPER_MILDocs /scratch/MultiR_01/Generalized_PERLOC_MILDocs")
 *      Keyword arg for Feature Generation class (ie, "-fg FeatureGeneratorDefaultPlusOne")
 *
 *      Keyword arguments can be in any order.  Rest of args should come first and maintain illustrated order.
 *
 * @author bobgian
 *
 */
public class DBPopulatePartitionedModelFiles
{
    private static Set<String> targetRelations = null;

    public static void main (String[] args) throws ParseException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException
    {
        List<String> arguments = new ArrayList<String>();
        for(String arg: args)
        {
            arguments.add(arg);
        }

        int numTestDocs = 300;
        CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
        FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
        ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
        List<SententialInstanceGeneration> sigs = CLIUtils.loadSententialInstanceGenerationList(arguments);
        List<String> modelPaths = CLIUtils.loadFilePaths(arguments);

        String annotationsInputFilePath = arguments.get(1);
        String evaluationRelationsFilePath = arguments.get(2);
        String pathToTestDocumentsFile = arguments.get(4);
        String experimentID = arguments.get(5);
        String experimentDate = arguments.get(6);
        String workerID = arguments.get(7);
        String voteExperID = arguments.get(8);
        String voteDate = arguments.get(9);
        String pathToSentencesFile = arguments.get(10);
        String pathToExtractionsVotesFile = arguments.get(11);

        File testDocsFile = new File(pathToTestDocumentsFile);
        if(! testDocsFile.exists() || ! testDocsFile.isFile())
        {
            throw new IllegalArgumentException("File at " + pathToTestDocumentsFile + " does not exist or is not a file.");
        }

        System.out.println("\nReading test document list ...");
        Map<String, Integer> testDocumentNameMap = new HashMap<String, Integer>(); // HashMap mapping documentID strings to array indices.
        String[] testDocumentNameArray = new String[numTestDocs];                  // ARRAY containing same documentID strings, for indexing with unique keys (array index).
        int idx = 0;
        BufferedReader br = new BufferedReader(new FileReader(testDocsFile));
        String nextLine;

        while((nextLine = br.readLine()) != null)
        {
            String docName = parseDocname(nextLine);
            testDocumentNameMap.put(docName, idx);
            testDocumentNameArray[idx] = docName;
            ++idx;
        }
        br.close();

        System.out.println("\nIndexing sentences in test document set ...");
        Map<Integer, Integer[]> testDocumentSentenceOffsets = new HashMap<Integer, Integer[]>(); // testDocumentSentenceOffsets: <corpusSentenceIndex> --> <sentenceOffsets[]>
        br = new BufferedReader(new FileReader(new File("/projects/WebWare6/Multir/MultirSystem/files/corpus/sentences.meta")));
        while((nextLine = br.readLine()) != null)
        {
            // <corpusSentenceIndex> is a corpus-wide unique integer sentence identifier (sentence text itself may NOT be unique) used as key to denote all document-relative sentence offsets and string contents.
            String[] stringItems = nextLine.split("\t");   // stringItems: <corpusSentenceIndex, DocumentName(with suffix), Tokenized-Text-of-Sentence>
            String docName = parseDocname(stringItems[1]); // docName is DocumentName without suffix.
            Integer docIndex = testDocumentNameMap.get(docName);
            if(docIndex != null)
            {
                int corpusSentenceIndex = Integer.parseInt(stringItems[0]);
                Integer[] sentenceOffsets = new Integer[] { docIndex, 0, 0 }; // sentenceOffsets: [ <index_of_DocumentName_in_testDocumentNameArray>, <dummy_zero>, <dummy_zero> ]
                testDocumentSentenceOffsets.put(corpusSentenceIndex, sentenceOffsets);
            }
        }
        br.close();

        System.out.println("\nReading document-relative sentence offsets ...");
        br = new BufferedReader(new FileReader(new File("/projects/WebWare6/Multir/MultirSystem/files/corpus/SENTOFFSETINFORMATION")));
        while((nextLine = br.readLine()) != null)
        {
            String[] stringItems = nextLine.split("\\s"); // <corpusSentenceIndex, SentenceDocumentRelativeStartOffset, SentenceDocumentRelativeEndOffset>
            Integer corpusSentenceIndex = Integer.parseInt(stringItems[0]);
            Integer[] sentenceOffsets = testDocumentSentenceOffsets.get(corpusSentenceIndex);
            if(sentenceOffsets != null)
            {
                sentenceOffsets[1] = Integer.parseInt(stringItems[1]);
                sentenceOffsets[2] = Integer.parseInt(stringItems[2]);
                // sentenceOffsets: [ <index_of_DocumentName_in_testDocumentNameArray>, <Int_Sent_DocRelative_Start_Offset>, <Int_Sent_DocRelative_End_Offset> ]
            }
        }
        br.close();
        // testDocumentSentenceOffsets: <corpusSentenceIndex> --> < [ <index_of_DocumentName_in_testDocumentNameArray>, <Int_Sent_DocRelative_Start_Offset>, <Int_Sent_DocRelative_End_Offset> ] >

        File sentencesFile = new File(pathToSentencesFile);
        if(sentencesFile.exists())
        {
            if(! sentencesFile.isFile())
            {
                throw new IllegalArgumentException("File at " + pathToSentencesFile + " exists but is not a file.");
            }
            System.out.println("\nSentences file already exists; not re-printing it.");
        }
        else
        {
            System.out.println("\nReading full list of sentences and printing Sentences file ...");
            br = new BufferedReader(new FileReader(new File("/projects/WebWare6/Multir/MultirSystem/files/corpus/SENTTEXTINFORMATION")));
            BufferedWriter bw = new BufferedWriter(new FileWriter(sentencesFile));
            while((nextLine = br.readLine()) != null)
            {
                String[] stringItems = nextLine.split("\t"); // <corpusSentenceIndex, StringTextOfSentence>
                Integer corpusSentenceIndex = Integer.parseInt(stringItems[0]);
                // testDocumentSentenceOffsets: < corpusSentenceIndex > --> < [ <index_of_DocumentName_in_testDocumentNameArray>, <Int_Sent_DocRelative_Start_Offset>, <Int_Sent_DocRelative_End_Offset> ] >
                Integer[] sentenceOffsets = testDocumentSentenceOffsets.get(corpusSentenceIndex);
                if(sentenceOffsets != null)
                {
                    //       DocumentName (suffix removed)                      DocRelativeSentenceStart    DocRelativeSentenceEnd      StringTextOfSentence
                    bw.write(testDocumentNameArray[sentenceOffsets[0]] + "\t" + sentenceOffsets[1] + "\t" + sentenceOffsets[2] + "\t" + stringItems[1] + "\n");
                }
            }
            br.close();
            bw.close();
        }

        System.out.println("\nMoving on to ExtractionsVotes file generation ...");
        targetRelations = EvaluationUtils.loadTargetRelations(evaluationRelationsFilePath);

        // Load test corpus.
        Corpus c = new Corpus(arguments.get(0), cis, true);

        // If corpus object is full corpus, we may specify to look at train or test partition of it based on a input file representing the names of the test documents.
        if(arguments.size() == 12)
        {
            String corpusSetting = arguments.get(3);
            if(!corpusSetting.equals("train") && !corpusSetting.equals("test"))
            {
                throw new IllegalArgumentException("This argument must be train or test.");
            }

            if(corpusSetting.equals("train"))
            {
                c.setCorpusToTrain(pathToTestDocumentsFile);
            }
            else
            {
                c.setCorpusToTest(pathToTestDocumentsFile);
            }
        }

        if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER)
        {
            FigerTypeUtils.init();
        }

        long start = System.currentTimeMillis();
        List<Extraction> extractions = getMultiModelExtractions(c, ai, fg, sigs, modelPaths, targetRelations);
        long end = System.currentTimeMillis();
        System.out.println("Got Extractions in " + (end-start));

        start = end;
        List<ExtractionAnnotation> annotations = EvaluationUtils.loadAnnotations(annotationsInputFilePath); // Gets LIST of all Annotation objects (each an Extraction + Label).
        Map<Extraction, Double> annotatedExtractions = new HashMap<Extraction, Double>();                   // Maps the Annotation object's Extraction to its Label (converted: true => 1.0, false => 0.0).
        for(ExtractionAnnotation ea : annotations)                                                          // Fill the HashMap from the List of Annotations.
        {
            Double vote;
            if(ea.getLabel())
            {
                vote = new Double(1.0);
            }
            else
            {
                vote = new Double(0.0);
            }
            annotatedExtractions.put(ea.getExtraction(), vote);
        }
        end = System.currentTimeMillis();
        System.out.println("Got (and Hashed) Annotations in " + (end-start));

        System.out.println("\nPrinting Partitioned ExtractionsVotes file ...");
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(pathToExtractionsVotesFile)));
        for(Extraction e : extractions)
        {
            int corpusSentenceIndex = e.getSentNum();
            Double voteObj = annotatedExtractions.get(e);
            double voteVal;
            if(voteObj == null)
            {
                voteVal = 0.5;
            }
            else
            {
                voteVal = (double)voteObj;
            }

            // <corpusSentenceIndex> is a corpus-wide unique integer sentence identifier (sentence text itself may NOT be unique) used as key to denote all document-relative sentence offsets and string contents.
            // testDocumentSentenceOffsets: < corpusSentenceIndex > --> < [ <index_of_DocumentName_in_testDocumentNameArray>, <Int_Sent_DocRelative_Start_Offset>, <Int_Sent_DocRelative_End_Offset> ] >
            // sentenceOffsets: [ <index_of_DocumentName_in_testDocumentNameArray>, <Int_Sent_DocRelative_Start_Offset>, <Int_Sent_DocRelative_End_Offset> ]
            Integer[] sentenceOffsets = testDocumentSentenceOffsets.get(corpusSentenceIndex);
            String docName = testDocumentNameArray[sentenceOffsets[0]]; // DocumentName (suffix removed)
            int sentenceStart = sentenceOffsets[1];                     // Document-relative sentence-start offset.
            int sentenceEnd = sentenceOffsets[2];                       // Document-relative sentence-end offset.
            int arg1start = e.getArg1().getStartOffset() + sentenceStart;
            int arg1end = e.getArg1().getEndOffset() + sentenceStart;
            int arg2start = e.getArg2().getStartOffset() + sentenceStart;
            int arg2end = e.getArg2().getEndOffset() + sentenceStart;
            String sentenceText = e.getSenText(); // String text of sentence (may not be unique in this document or all documents collectively).
            bw.write("MultiR" + "\t" + e.getScore() + "\t" + docName + "\t" + e.getRelation() + "\t" + "FreeBase" + "\t"
                     + arg1start + "\t" + arg1end + "\t" + e.getArg1().getArgName() + "\t"
                     + arg2start + "\t" + arg2end + "\t" + e.getArg2().getArgName() + "\t"
                     + sentenceStart + "\t" + sentenceEnd + "\t"
                     + experimentID + "\t" + experimentDate + "\t" + workerID + "\t" + voteVal + "\t" + voteExperID + "\t" + voteDate + "\t"
                     + sentenceText + "\n");
        }
        bw.close();

        if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER)
        {
            FigerTypeUtils.close();
        }
    }

    private static String parseDocname(String docName)
    {
        String[] docID = docName.split("\\."); // Remove the suffix ".LDC*" on the DocID string here.
        return docID[0] + "." + docID[1];
    }


    private static List<Extraction> getMultiModelExtractions(Corpus c, ArgumentIdentification ai, FeatureGenerator fg, List<SententialInstanceGeneration> sigs, List<String> modelPaths, Set<String> targetRelations) throws SQLException, IOException
    {
        List<Extraction> extrs = new ArrayList<Extraction>();
        for(int i =0; i < sigs.size(); i++)
        {
            Iterator<Annotation> docs = c.getDocumentIterator();
            SententialInstanceGeneration sig = sigs.get(i);
            String modelPath = modelPaths.get(i);
            DocumentExtractor de = new DocumentExtractor(modelPath, fg, ai, sig);

            Map<String, Integer> rel2RelIdMap =de.getMapping().getRel2RelID();
            Map<Integer, String> ftID2ftMap = ModelUtils.getFeatureIDToFeatureMap(de.getMapping());

            int docCount = 0;
            while(docs.hasNext())
            {
                Annotation doc = docs.next();
                List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
                for(CoreMap sentence : sentences)
                {
                    //argument identification
                    List<Argument> arguments = ai.identifyArguments(doc, sentence);
                    //sentential instance generation
                    List<Pair<Argument, Argument>> sententialInstances = sig.generateSententialInstances(arguments, sentence);
                    for(Pair<Argument, Argument> p : sententialInstances)
                    {
                        Pair<Triple<String, Double, Double>, Map<Integer, Map<Integer, Double>>> extrResult = de.extractFromSententialInstanceWithAllFeatureScores(p.first, p.second, sentence, doc);
                        if(extrResult != null)
                        {
                            Triple<String, Double, Double> extrScoreTripe = extrResult.first;
                            Map<Integer, Double> featureScores = extrResult.second.get(rel2RelIdMap.get(extrResult.first.first));
                            String rel = extrScoreTripe.first;
                            List<Pair<String, Double>> featureScoreList = EvaluationUtils.getFeatureScoreList(featureScores, ftID2ftMap);
                            if(targetRelations.contains(rel))
                            {
                                if(!(rel.equals("/organization/organization/founders") && (sig instanceof FigerAndNERTypeSignaturePERPERSententialInstanceGeneration)))
                                {
                                    String docName = sentence.get(SentDocName.class);
                                    String senText = sentence.get(CoreAnnotations.TextAnnotation.class);
                                    Integer sentNum = sentence.get(SentGlobalID.class);
                                    Extraction e = new Extraction(p.first, p.second, docName, rel, sentNum, extrScoreTripe.third, senText);
                                    e.setFeatureScoreList(featureScoreList);
                                    extrs.add(e);
                                }
                            }
                        }
                    }
                }
                ++docCount;
                if(docCount % 100 == 0)
                {
                    System.out.println(docCount + " docs processed");
                }
            }
        }
        return EvaluationUtils.getUniqueList(extrs);

    }

    private static void logNegativeExtraction(Argument first, Argument second, CoreMap sentence, Pair<Triple<String, Double, Double>, Map<Integer, Map<Integer, Double>>> extrResult, DocumentExtractor de, Map<Integer, String> ftID2ftMap)
    {
        System.out.println(sentence.get(SentGlobalID.class) + "\t" + extrResult.first.first + "\t" + extrResult.first.third + "\t" + first.getArgName() + "\t" + second.getArgName() + "\t" + sentence.get(CoreAnnotations.TextAnnotation.class));

        Map<String, Integer> rel2RelIDMap = de.getMapping().getRel2RelID();

        for(String rel: rel2RelIDMap.keySet())
        {
            Integer intRel = rel2RelIDMap.get(rel);

            System.out.println("Features for rel: " + rel);
            Map<Integer, Double> featureMap = extrResult.second.get(intRel);
            for(Integer featureI : featureMap.keySet())
            {
                String feat = ftID2ftMap.get(featureI);
                Double score = featureMap.get(featureI);
                System.out.println(feat + "\t" + score);
            }
        }
    }
}

//===================================================================
