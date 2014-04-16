package edu.washington.multir.argumentidentification;

import edu.washington.multir.util.TypeConstraintUtils;

/**
 * Populates <code>FigerAndNERTypeSignatureSententialInsantceGeneration</code> instance
 * with types of <code>GeneralType.PERSON</code> and <code>GeneralType.PERSON</code>
 * @author jgilme1
 *
 */
public class FigerAndNERTypeSignaturePERPERSententialInstanceGeneration extends
FigerAndNERTypeSignatureSententialInstanceGeneration {
	
	private static FigerAndNERTypeSignaturePERPERSententialInstanceGeneration instance = null;
	private FigerAndNERTypeSignaturePERPERSententialInstanceGeneration(String arg1Type, String arg2Type){
		super(arg1Type,arg2Type);
	}
	public static FigerAndNERTypeSignaturePERPERSententialInstanceGeneration getInstance(){
		if(instance == null) {
			instance = new FigerAndNERTypeSignaturePERPERSententialInstanceGeneration(TypeConstraintUtils.GeneralType.PERSON,
					TypeConstraintUtils.GeneralType.PERSON);
		}
		return instance;
		}
	
}
