package edu.washington.multir.corpus;

import java.util.List;

public class CustomCorpusInformationSpecification extends DefaultCorpusInformationSpecification {

	
	public void addSentenceInformation(List<SentInformationI> sentInformationList){
		for(SentInformationI sentInformation: sentInformationList){
			this.sentenceInformation.add(sentInformation);
		}
	}
	
	public void addTokenInformation(List<TokenInformationI> tokenInformationList){
		for(TokenInformationI tokenInformation: tokenInformationList){
			this.tokenInformation.add(tokenInformation);
		}
	}
	
	public void addDocumentInformation(List<DocumentInformationI> documentInformationList){
		for(DocumentInformationI documentInformation: documentInformationList){
			this.documentInformation.add(documentInformation);
		}
	}
}
