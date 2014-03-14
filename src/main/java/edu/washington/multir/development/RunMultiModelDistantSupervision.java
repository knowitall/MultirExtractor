package edu.washington.multir.development;

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
import edu.washington.multir.data.TypeSignatureRelationMap;
import edu.washington.multir.distantsupervision.MultiModelDistantSupervision;
import edu.washington.multir.distantsupervision.NegativeExampleCollection;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.FigerTypeUtils;

public class RunMultiModelDistantSupervision {
	
	/**
	 * 
	 * @param args
	 * 		args[0] should be name of corpus database
	 * 		args[1] should be relationKBFilePath
	 * 	    args[2] should be entityKBFielPath
	 * 	    args[3] should be targetRelationsFilePath
	 *      args[4] should be true / false for using the new negative example collection algorithm
	 *      
	 *      -ai defines ArgumentIdentification
	 *      -siglist defines list of SententialInstanceGeneration algorithms
	 *      -files defines list of output Distant Supervision files
	 *      -rm defines RelationMatching algorithm
	 *      
	 *      -si defines SententialInformation in Corpus Representation
	 *      -di defines documentInformation in Corpus Representation
	 *      -ti defines TokenInformation in Corpus Representation
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
		List<SententialInstanceGeneration> sigList = CLIUtils.loadSententialInstanceGenerationList(arguments);
		List<String> paths = CLIUtils.loadFilePaths(arguments);
		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		RelationMatching rm = CLIUtils.loadRelationMatching(arguments);
		NegativeExampleCollection nec = CLIUtils.loadNegativeExampleCollection(arguments);

		Corpus c = new Corpus(arguments.get(0),cis,true);
		KnowledgeBase kb = new KnowledgeBase(arguments.get(1),arguments.get(2),arguments.get(3));
		Boolean newNegativeExampleCollection = Boolean.parseBoolean(arguments.get(4));
		
		MultiModelDistantSupervision ds = new MultiModelDistantSupervision(ai,paths,sigList,rm,nec,newNegativeExampleCollection);
		FigerTypeUtils.init();
		//init type signature relation map
		TypeSignatureRelationMap.init(arguments.get(4));
		ds.run(kb,c);
		FigerTypeUtils.close();
	}

}
