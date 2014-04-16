package edu.washington.multir.development;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.ParseException;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.util.CLIUtils;


public class LoadCorpus {
	/**
	 * args[0] - name of corpus database
	 * args[1] - path to the corpus information directory with raw Corpus files
	 * args[2] - name of temporary sentence file for batch insertion into Derby DB
	 * args[3] - name of temporary document file for batch insertion into Derby DB
	 * args[4-...] - Use option -si to declare a list of SentInformationI class names, option -di 
	 * for DocumentInformationI class names and -ti for TokenInformationI class names, these
	 * classes will be used to extend the DefaultCorpusInformationSpecification
	 * there should be an option value for the CorpusInformationSpecification class
	 * @param args
	 * @throws SQLException
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws ParseException 
	 */
	public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, ParseException{
		
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
    	Corpus c = new Corpus(arguments.get(0),cis,false);
    	long start = System.currentTimeMillis();
    	c.loadCorpus(new File(arguments.get(1)), arguments.get(2), arguments.get(3));
    	long end = System.currentTimeMillis();
    	System.out.println("Loading DB took " + (end-start) + " millisseconds");    	
	}
}
