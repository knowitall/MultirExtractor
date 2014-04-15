package edu.washington.multir.development;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification;
import edu.washington.multir.featuregeneration.DefaultFeatureGenerator;
import edu.washington.multir.featuregeneration.FeatureGeneration;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.FigerTypeUtils;

public class RunFeatureGeneration {
	
	
	/**
	 * 
	 * @param args
	 * 		args[0] is path to Corpus DB file
	 *      -si option takes list of SentInformationI class names
	 *      -di option takes list of DocumentInformationI class names
	 *      -ti option takes list of TokenInformationI class names
	 *      -fg is required and takes single argument of FeatureGenerator class name

	 * @throws SQLException
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 * @throws ParseException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 */
	public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException, ParseException, InstantiationException, IllegalAccessException, InterruptedException, ExecutionException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException{
		
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
		FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
		List<String> featureFilePaths = CLIUtils.loadFeatureFilePaths(arguments);
		List<String> DSFilePaths = CLIUtils.loadDSFilePaths(arguments);
		
		if(featureFilePaths.size()!=DSFilePaths.size()){
			throw new IllegalArgumentException("size of feature files must equal size of ds files");
		}

		
		Corpus c = new Corpus(arguments.get(0),cis,true);
		
		FeatureGeneration featureGeneration = new FeatureGeneration(fg);
		FigerTypeUtils.init();
		featureGeneration.run(DSFilePaths,featureFilePaths,c,cis);
		FigerTypeUtils.close();
	}
}
