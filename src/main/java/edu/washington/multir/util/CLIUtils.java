package edu.washington.multir.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CustomCorpusInformationSpecification;
import edu.washington.multir.corpus.DocumentInformationI;
import edu.washington.multir.corpus.SentInformationI;
import edu.washington.multir.corpus.TokenInformationI;
import edu.washington.multir.distantsupervision.NegativeExampleCollection;
import edu.washington.multir.featuregeneration.FeatureGenerator;

public class CLIUtils {
	
	/**
	 * Returns A CorpusInformationSpecification object using the proper 
	 * command line options -si, -di, or -ti. These options can have 
	 * a list of arguments so any non-option arguments should be placed
	 * before all of these options in the command line argument string
	 * @return
	 * @throws ParseException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public static CorpusInformationSpecification loadCorpusInformationSpecification(List<String> args) throws ParseException, ClassNotFoundException, InstantiationException, IllegalAccessException{
		Options options = new Options();
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All SentInformationI to be added to CorpusSpecification");
		options.addOption(OptionBuilder.create("si"));
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All DocInformationI to be added to CorpusSpecification");
		options.addOption(OptionBuilder.create("di"));
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All TokenInformationI to be added to CorpusSpecification");
		options.addOption(OptionBuilder.create("ti"));
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForMultiValueOptions(args,"si","di","ti");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(args.get(i));
		}
		for(Integer i =0; i < args.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(args.get(i));
			}
		}
		
		
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		CustomCorpusInformationSpecification cis = new CustomCorpusInformationSpecification();
		
		String[] sentInformationSpecificationClasses = cmd.getOptionValues("si");
		if(sentInformationSpecificationClasses != null){
			List<SentInformationI> sentenceInformation = new ArrayList<>();
			for(String sentInformationSpecificationClass : sentInformationSpecificationClasses){
				ClassLoader cl = ClassLoader.getSystemClassLoader();
				String corpusInformationClassPrefix = "edu.washington.multir.corpus.";
				Class<?> sentInformationClass = cl.loadClass(corpusInformationClassPrefix+sentInformationSpecificationClass);
				sentenceInformation.add((SentInformationI)sentInformationClass.newInstance());
			}
			cis.addSentenceInformation(sentenceInformation);
		}
		
		String[] documentInformationSpecificationClasses = cmd.getOptionValues("di");
		if(documentInformationSpecificationClasses!=null){
			List<DocumentInformationI> docInformation = new ArrayList<>();
			for(String docInformationSpecification : documentInformationSpecificationClasses){
				ClassLoader cl = ClassLoader.getSystemClassLoader();
				String corpusInformationClassPrefix = "edu.washington.multir.corpus.";
				Class<?> documentInformationClass = cl.loadClass(corpusInformationClassPrefix+docInformationSpecification);
				docInformation.add((DocumentInformationI)documentInformationClass.newInstance());
			}
			cis.addDocumentInformation(docInformation);
		}
		
		
		String[] tokenInformationSpecificationClasses = cmd.getOptionValues("ti");
		if(tokenInformationSpecificationClasses != null){
			List<TokenInformationI> tokInformation = new ArrayList<>();
			for(String tokenInformationSpecificationClass : tokenInformationSpecificationClasses){
				ClassLoader cl = ClassLoader.getSystemClassLoader();
				String corpusInformationClassPrefix = "edu.washington.multir.corpus.";
				Class<?> tokenInformationClass = cl.loadClass(corpusInformationClassPrefix+tokenInformationSpecificationClass);
				tokInformation.add((TokenInformationI)tokenInformationClass.newInstance());
			}
			cis.addTokenInformation(tokInformation);
		}
		
		removeUsedArguments(remainingArguments,args);
		
		
		return cis;
	}

	private static List<Integer> getContiguousArgumentsForMultiValueOptions(
			List<String> args, String ... options) {
		List<String> optionList = new ArrayList<String>();
		List<Integer> relevantTokens = new ArrayList<Integer>();
		for(String opt: options){
			optionList.add(opt);
		}
		
		boolean foundTargetOption = false;
		for(Integer i = 0; i < args.size(); i++){
			String iString = args.get(i);
			if(isOption(iString)){
				if(optionList.contains(iString.substring(1))){
					relevantTokens.add(i);
					foundTargetOption = true;
				}
				else{
					foundTargetOption = false;
				}
			}
			else{
				if(foundTargetOption){
					relevantTokens.add(i);
				}
			}
		}
		
		return relevantTokens;
	}
	
	private static List<Integer> getContiguousArgumentsForSingleValueOptions(
			List<String> args, String ... options) {
		List<String> optionList = new ArrayList<String>();
		List<Integer> relevantTokens = new ArrayList<Integer>();
		for(String opt: options){
			optionList.add(opt);
		}
		
		boolean foundTargetOption = false;
		for(Integer i = 0; i < args.size(); i++){
			String iString = args.get(i);
			if(isOption(iString)){
				if(optionList.contains(iString.substring(1))){
					relevantTokens.add(i);
					foundTargetOption = true;
				}
				else{
					foundTargetOption = false;
				}
			}
			else{
				if(foundTargetOption){
					relevantTokens.add(i);
					foundTargetOption=false;
				}
			}
		}
		
		return relevantTokens;
	}
	
	private static boolean isOption(String str){
		if(str.startsWith("-")){
			return true;
		}
		else{
			return false;
		}
	}


	/**
	 * Loads the argumentIdenficiation object from the -ai command line
	 * option. The value of this option should be the name of the ArgumentIdentification
	 * alrogrithm in the package edu.washington.multir.argumentidentification
	 * @param arguments
	 * @return
	 * @throws ParseException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public static ArgumentIdentification loadArgumentIdentification(
			List<String> arguments) throws ParseException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Options options = new Options();
		options.addOption("ai",true,"argumentIdentification algorithm class");
		
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForSingleValueOptions(arguments,"ai");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		ArgumentIdentification ai = null;
		String argumentIdentificationName = cmd.getOptionValue("ai");
		if(argumentIdentificationName != null){
			String argumentIdentificationClassPrefix = "edu.washington.multir.argumentidentification.";
			Class<?> c = cl.loadClass(argumentIdentificationClassPrefix+argumentIdentificationName);
			Method m = c.getMethod("getInstance");
			ai = (ArgumentIdentification) m.invoke(null);
		}
		else{
			throw new IllegalArgumentException("argumentIdentification Class Argument is invalid");
		}

		removeUsedArguments(remainingArguments,arguments);
		return ai;
	}
	
	

	/**
	 * Clears first argument, and sets the values of the second argument
	 * to the first argument.
	 * @param remainingArgs
	 * @param arguments
	 */
	private static void removeUsedArguments(List<String> remainingArgs,
			List<String> arguments) {
		//update parameter args
		arguments.clear();
		arguments.addAll(remainingArgs);
	}

	/**
	 * Loads the SententialInstanceGeneration object from the -sig option.
	 * The value of this option should be the name of the SententialInstanceGeneration
	 * class in the package edu.washington.multir.algorithmidentification
	 * @param arguments
	 * @return
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws ParseException
	 */
	public static SententialInstanceGeneration loadSententialInformationGeneration(
			List<String> arguments) throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException {
		Options options = new Options();
		options.addOption("sig",true,"sententialInstanceGeneration algorithm class");
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForSingleValueOptions(arguments,"sig");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));

		ClassLoader cl = ClassLoader.getSystemClassLoader();
		SententialInstanceGeneration sig = null;

		String sententialInstanceGenerationName = cmd.getOptionValue("sig");

		if(sententialInstanceGenerationName != null){
			String sententialInstanceClassPrefix = "edu.washington.multir.argumentidentification.";
			Class<?> c = cl.loadClass(sententialInstanceClassPrefix+sententialInstanceGenerationName);
			Method m = c.getMethod("getInstance");
			sig = (SententialInstanceGeneration) m.invoke(null);
		}
		else{
			throw new IllegalArgumentException("sententialInstanceGeneration Class Argument is invalid");
		}
		
		removeUsedArguments(remainingArguments,arguments);
		
		return sig;
	}

	/**
	 * Loads the relationMatching object from the -rm option.
	 * The value of this option should be the name of RelationMatching
	 * class in the package edu.washington.multir.algorithmidentification
	 * @param arguments
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 * @throws ParseException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public static RelationMatching loadRelationMatching(List<String> arguments) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, ParseException, NoSuchMethodException, SecurityException {
		Options options = new Options();
		options.addOption("rm",true,"relationMatching algorithm class");
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForSingleValueOptions(arguments,"rm");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		RelationMatching rm = null;
		
		String relationMatchingName = cmd.getOptionValue("rm");

		
		if(relationMatchingName != null){
			String relationMatchingClassPrefix = "edu.washington.multir.argumentidentification.";
			Class<?> c = cl.loadClass(relationMatchingClassPrefix+relationMatchingName);
			Method m = c.getMethod("getInstance");
			rm = (RelationMatching) m.invoke(null);
		}
		else{
			throw new IllegalArgumentException("relationMatching Class Argument is invalid");
		}
		
		removeUsedArguments(remainingArguments,arguments);
		return rm;
	}
	
	/**
	 * Loads a featureGenerator object from the -fg option.
	 * The value of this option should be the name of FeatureGenerator
	 * class in the package edu.washingotn.multir.featuregeneration
	 * @param arguments
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 * @throws ParseException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws InstantiationException
	 */
	public static FeatureGenerator loadFeatureGenerator(List<String> arguments) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, ParseException, NoSuchMethodException, SecurityException, InstantiationException {
		Options options = new Options();
		options.addOption("fg",true,"featureGenerator algorithm class");
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForSingleValueOptions(arguments,"fg");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		FeatureGenerator fg = null;
		
		String featureGeneratorName = cmd.getOptionValue("fg");

		
		if(featureGeneratorName != null){
			String featureGeneratorClassPrefix = "edu.washington.multir.featuregeneration.";
			Class<?> c = cl.loadClass(featureGeneratorClassPrefix+featureGeneratorName);
			fg = (FeatureGenerator) c.newInstance();
		}
		else{
			throw new IllegalArgumentException("featureGenerator Class Argument is invalid");
		}
		
		removeUsedArguments(remainingArguments,arguments);
		return fg;
	}

	
	/**
	 * Loads the NegativeExampleCollection object from the -nec option.
	 * The value of this option should be th name of the NegativeExampleCollection
	 * class in the package edu.washington.multir.distantsupervision
	 * @param arguments
	 * @return
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws ParseException
	 */
	public static NegativeExampleCollection loadNegativeExampleCollection(
			List<String> arguments) throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException {
		Options options = new Options();
		options.addOption("nec",true,"negativeExample collection algorithm class");
		options.addOption("ratio",true,"negative Example to positive example ratio");
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForSingleValueOptions(arguments,"nec","ratio");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		
		
		
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		NegativeExampleCollection nec = null;
		double ratio;
		
		String negativeExampleCollectionName = cmd.getOptionValue("nec");
		String ratioString = cmd.getOptionValue("ratio");
		
		if(ratioString != null){
			ratio =Double.parseDouble(ratioString);
		}
		else{
			throw new IllegalArgumentException("ratio argument is invalid");
		}
		
		if(negativeExampleCollectionName != null){
			String relationMatchingClassPrefix = "edu.washington.multir.distantsupervision.";
			Class<?> c = cl.loadClass(relationMatchingClassPrefix+negativeExampleCollectionName);
			Method m = c.getMethod("getInstance",double.class);
			nec = (NegativeExampleCollection) m.invoke(null,ratio);
		}
		else{
			throw new IllegalArgumentException("negativeExampleCollection Class Argument is invalid");
		}
		
		removeUsedArguments(remainingArguments,arguments);
		return nec;
	}

	public static List<ArgumentIdentification> loadArgumentIdentificationList(
			List<String> arguments) throws ClassNotFoundException, ParseException,
			NoSuchMethodException, SecurityException, IllegalAccessException, 
			IllegalArgumentException, InvocationTargetException {
		Options options = new Options();
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All Argument Identification algorithms");
		options.addOption(OptionBuilder.create("ailist"));
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForMultiValueOptions(arguments,"ailist");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		List<ArgumentIdentification> aiList = new ArrayList<>();
		
		String[] argumentIdentificationClasses = cmd.getOptionValues("ailist");
		if(argumentIdentificationClasses != null){
			for(String argumentIdentificationClass : argumentIdentificationClasses){
				ClassLoader cl = ClassLoader.getSystemClassLoader();
				String argumentIdentificationClassPrefix = "edu.washington.multir.argumentidentification.";
				Class<?> sentInformationClass = cl.loadClass(argumentIdentificationClassPrefix+argumentIdentificationClass);
				Method m = sentInformationClass.getMethod("getInstance");
				aiList.add((ArgumentIdentification) m.invoke(null));
			}
		}
		removeUsedArguments(remainingArguments,arguments);
		
		
		return aiList;
	}
	
	public static List<SententialInstanceGeneration> loadSententialInstanceGenerationList(
			List<String> arguments) throws ClassNotFoundException, ParseException,
			NoSuchMethodException, SecurityException, IllegalAccessException, 
			IllegalArgumentException, InvocationTargetException {
		Options options = new Options();
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All Sentential Instance Generation algorithms");
		options.addOption(OptionBuilder.create("siglist"));
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForMultiValueOptions(arguments,"siglist");
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		
		
		
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		List<SententialInstanceGeneration> sigList = new ArrayList<>();
		
		String[] sententialInstanceGenerationCLasses = cmd.getOptionValues("siglist");
		if(sententialInstanceGenerationCLasses != null){
			for(String sententialInstanceGenerationCLass : sententialInstanceGenerationCLasses){
				ClassLoader cl = ClassLoader.getSystemClassLoader();
				String argumentIdentificationClassPrefix = "edu.washington.multir.argumentidentification.";
				Class<?> sentInformationClass = cl.loadClass(argumentIdentificationClassPrefix+sententialInstanceGenerationCLass);
				Method m = sentInformationClass.getMethod("getInstance");
				sigList.add((SententialInstanceGeneration) m.invoke(null));
			}
		}
		removeUsedArguments(remainingArguments,arguments);
		
		
		return sigList;
	}
	
	public static List<String> loadFilePaths(List<String> arguments, String optionName) throws ParseException {
		Options options = new Options();
		OptionBuilder.hasArgs(10);
		OptionBuilder.withDescription("List of All Paths for Each Argument Identification Class");
		options.addOption(OptionBuilder.create(optionName));
		
		List<Integer> relevantArgIndices = getContiguousArgumentsForMultiValueOptions(arguments,optionName);
		List<String> relevantArguments = new ArrayList<String>();
		List<String> remainingArguments = new ArrayList<String>();
		for(Integer i: relevantArgIndices){
			relevantArguments.add(arguments.get(i));
		}
		for(Integer i =0; i < arguments.size(); i++){
			if(!relevantArgIndices.contains(i)){
				remainingArguments.add(arguments.get(i));
			}
		}
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, relevantArguments.toArray(new String[relevantArguments.size()]));
		
		List<String> pathList = new ArrayList<>();
		
		String[] paths = cmd.getOptionValues(optionName);
		for(String path: paths){
			pathList.add(path);
		}
		removeUsedArguments(remainingArguments,arguments);
		return pathList;
	}
	
	public static List<String> loadFilePaths(List<String> arguments) throws ParseException{
		return loadFilePaths(arguments,"files");
	}

	public static List<String> loadFeatureFilePaths(List<String> arguments) throws ParseException{
		return loadFilePaths(arguments,"featureFiles");
	}
	
	public static List<String> loadDSFilePaths(List<String> arguments) throws ParseException{
		return loadFilePaths(arguments,"dsFiles");
	}

	public static List<String> loadOutputFilePaths(List<String> arguments) throws ParseException {
		return loadFilePaths(arguments,"outputFiles");

	}

}
