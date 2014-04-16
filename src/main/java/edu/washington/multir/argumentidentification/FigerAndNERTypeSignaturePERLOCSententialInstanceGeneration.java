package edu.washington.multir.argumentidentification;

import edu.washington.multir.util.TypeConstraintUtils;

/**
 * Populates <code>FigerAndNERTypeSignatureSententialInsantceGeneration</code> instance
 * with types of <code>GeneralType.PERSON</code> and <code>GeneralType.LOCATION</code>
 * @author jgilme1
 *
 */
public class FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration extends
FigerAndNERTypeSignatureSententialInstanceGeneration {
	
	private static FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration instance = null;
	private FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration(String arg1Type, String arg2Type){
		super(arg1Type,arg2Type);
	}
	public static FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration getInstance(){
		if(instance == null) {
			instance = new FigerAndNERTypeSignaturePERLOCSententialInstanceGeneration(TypeConstraintUtils.GeneralType.PERSON,
					TypeConstraintUtils.GeneralType.LOCATION);
		}
		return instance;
		}
	
}
