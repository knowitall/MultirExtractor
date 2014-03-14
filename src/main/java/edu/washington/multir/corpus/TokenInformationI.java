package edu.washington.multir.corpus;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;

public interface TokenInformationI {
	void read(String line, List<CoreLabel> tokens);
	String write(List<CoreLabel> tokens);
	String name();
}
