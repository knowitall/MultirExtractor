import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.NERArgumentIdentification;
import edu.washington.multir.argumentidentification.NERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification;
import edu.washington.multir.data.Argument;


public class TestNERArgumentIdentification {
	
	@Test
	public void testArgumentIdentification() throws SQLException{
		
		//connecto to training corpus
		CorpusInformationSpecification ci = new DefaultCorpusInformationSpecification();
		Corpus c = new Corpus("TrainingCorpusDatabase",ci,true);
		ArgumentIdentification ai = NERArgumentIdentification.getInstance();
		
		Annotation doc = c.getDocument("XIN_ENG_20021028.0184.LDC2007T07");
		List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
		CoreMap testSentence = sentences.get(10);

		List<Argument> args = ai.identifyArguments(doc, testSentence);
		Argument arg1 = args.get(0);
		Argument arg2 = args.get(1);
		Argument arg3 = args.get(2);
		
		assertEquals("Argument 1 = China Southwest Airlines","China Southwest Airlines",arg1.getArgName());
		assertEquals("Argument 2 = China National Airlines", "China National Airlines",arg2.getArgName());
		assertEquals("Argument 2 = CNAH", "CNAH",arg3.getArgName());
	}
}

