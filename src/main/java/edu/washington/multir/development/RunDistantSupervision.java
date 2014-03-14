package edu.washington.multir.development;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.NELArgumentIdentification;
import edu.washington.multir.argumentidentification.NELRelationMatching;
import edu.washington.multir.argumentidentification.NERArgumentIdentification;
import edu.washington.multir.argumentidentification.NERRelationMatching;
import edu.washington.multir.argumentidentification.NERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL;
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
	 * 		args[1] should be relationKBFilePath
	 * 	    args[2] should be entityKBFielPath
	 * 	    args[3] should be targetRelationsFilePath
	 *      args[4] should be true / false for negative examples
	 *      args[5] is optional, and is a ratio of positive to negative examples
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
		
		DistantSupervision ds = new DistantSupervision(ai,sig,rm,nec);
		FigerTypeUtils.init();
		ds.run(dsFileName,kb,c);
		FigerTypeUtils.close();
	}
}
