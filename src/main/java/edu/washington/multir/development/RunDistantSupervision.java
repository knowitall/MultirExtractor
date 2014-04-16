package edu.washington.multir.development;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.ParseException;

import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.distantsupervision.DistantSupervision;
import edu.washington.multir.distantsupervision.NegativeExampleCollection;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.FigerTypeUtils;

/**
 * An app for running distant supervision
 * @author jgilme1
 *
 */
public class RunDistantSupervision {
	
	/**
	 * 
	 * @param args
	 * 		args[0] should be name of corpus database
	 * 		args[1] should be distant supervision file
	 * 	    args[2] should be entityKBRelationsFile
	 * 	    args[3] should be entityKBEntityFile
	 * 		args[4] should be targetRelationsFile
	 *      args[5] is optional and should be either train or test
	 *      args[6] is optional and is path to the testDocumentsFile
	 *      -si option takes list of SentInformationI class names
	 *      -di option takes list of DocumentInformationI class names
	 *      -ti option takes list of TokenInformationI class names
	 *      -ai is required and takes single argument of ArgumentIdentification class name
	 *      -sig is required and takes single argument of SententialInstanceGeneration class name
	 *      -rm is required and takes single argument of RelationMatching class name
	 *      -nec is required and takes single argument of NegativeExampleCollection class name
	 *      -ratio is required and takes single argument float for ratio of Negative to POsitive training instances
	 * @throws SQLException
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 * @throws ParseException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InstantiationException 
	 */

	
	
	public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException, 
	ParseException, IllegalAccessException, IllegalArgumentException, 
	InvocationTargetException, NoSuchMethodException, SecurityException, 
	InstantiationException{
		
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		SententialInstanceGeneration sig = CLIUtils.loadSententialInformationGeneration(arguments);
		RelationMatching rm = CLIUtils.loadRelationMatching(arguments);
		NegativeExampleCollection nec = CLIUtils.loadNegativeExampleCollection(arguments);
		Corpus c = new Corpus(arguments.get(0),cis,true);
		String dsFileName = arguments.get(1);
		KnowledgeBase kb = new KnowledgeBase(arguments.get(2),arguments.get(3),arguments.get(4));

		//if corpus object is full corpus, we may specify to look at train or test
		//partition of it based on a input file representing the names of the test documents
		if(arguments.size() == 7){
			String corpusSetting = arguments.get(5);
			String pathToTestDocumentFile = arguments.get(6);
			
			if(!corpusSetting.equals("train") && !corpusSetting.equals("test")){
				throw new IllegalArgumentException("This argument must be train or test");
			}
			File f = new File(pathToTestDocumentFile);
			if(!f.exists() || !f.isFile()){
				throw new IllegalArgumentException("File at " + pathToTestDocumentFile + " does not exist or is not a file");
			}
			
			if(corpusSetting.equals("train")){
				c.setCorpusToTrain(pathToTestDocumentFile);
			}
			else{
				c.setCorpusToTest(pathToTestDocumentFile);
			}
		}
		DistantSupervision ds = new DistantSupervision(ai,sig,rm,nec);
		FigerTypeUtils.init();
		ds.run(dsFileName,kb,c);
		FigerTypeUtils.close();
	}
}
