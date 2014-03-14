package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import edu.stanford.nlp.util.Pair;

/**
 * This class is used to conver the annotations in the format
 * from the original paper to a a format used in the scoring
 * method in the SententialEvaluation class.
 * @author jgilme1
 *
 */
public class ConvertToADEPTBenchmark {
	
	public static void main(String[] args) throws IOException{
		
		String fileName = args[0];
		BufferedReader br = new BufferedReader( new FileReader(new File(fileName)));
		int count =0;
		String line;
		while((line = br.readLine())!=null){
			String [] values = line.split("\t");
			String annotatedSentence = values[5];
			String tokenizedSentence = values[8];
			String rel = values[3];
			String arg1String = values[6];
			String arg2String = values[7];
			boolean l = (values[4].equals("y") || values[4].equals("indirect"))? true : false;
			String unTokenizedSentence = SententialEvaluation.convertTokenizedSentence(tokenizedSentence);
			
			
			Integer arg1Occurrence = SententialEvaluation.getArgumentOccurrence(annotatedSentence, SententialEvaluation.argument1Pattern);
			Integer arg2Occurrence = SententialEvaluation.getArgumentOccurrence(annotatedSentence, SententialEvaluation.argument2Pattern);
			
			
			Pair<Integer,Integer> arg1Offsets = SententialEvaluation.getOffsetsOfArgument(arg1String, arg1Occurrence, unTokenizedSentence);
			Pair<Integer,Integer> arg2Offsets = SententialEvaluation.getOffsetsOfArgument(arg2String, arg2Occurrence, unTokenizedSentence);
			
			
			//output appropriate annotations...
			
			if((arg1Offsets!=null) && (arg2Offsets!=null)){
				if(!rel.equals("/location/administrative_division/country")){
					if(values[4].equals("y") || values[4].equals("n") || values[4].equals("indirect")){						
						File outputFile = new File("inputFile"+count+".txt");
						File referenceFile = new File("inputFile"+count+".txt.ref");
						
						BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
						BufferedWriter bw1 = new BufferedWriter(new FileWriter(referenceFile));
						
						bw.write(unTokenizedSentence+"\n");
						bw1.write(arg1Offsets.first + "\t" + arg1Offsets.second + "\t" + arg2Offsets.first + "\t" + arg2Offsets.second + "\t" + rel + "\t" + values[4]+"\n");
						
						bw.close();
						bw1.close();
					//	count++;
					}
				}
			}
			count++;
		}
		
		
		
		
		br.close();
		
	}

}
