package edu.washington.multir.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL.SentNamedEntityLinkingInformation.NamedEntityLinkingAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;

public class FigerTypeUtils {
	public static void main(String[] args) {
		init();
		//String fbType = "/government/us_president";
		Set<String> fbTypes = getFreebaseTypesFromID(GuidMidConversion.convertBackward("/m/0269xm9"));
		System.out.println("FB Types:");
		for(String fType: fbTypes){
			System.out.println(fType);
		}
		close();
	}

	private static Connection conn = null;
	private static PreparedStatement guidQuery = null;
	private static PreparedStatement typeQuery = null;
	private static PreparedStatement bigTypeQuery = null;
	public final static String typeFile = "types.map";
	public static Hashtable<String, String> mapping = null;
	

	public static void init() {
		try {
			// initialize the db connection
			conn = DriverManager
					.getConnection("jdbc:postgresql://pardosa05.cs.washington.edu:5432/wex?user=jgilme1"
							+ "&charSet=UTF8");
			guidQuery = conn
					.prepareStatement("select guid from freebase_names where name=?");
			typeQuery = conn
					.prepareStatement("select type from freebase_types where guid=?");
			
			StringBuilder bigTypeQueryBuilder = new StringBuilder();
			bigTypeQueryBuilder.append("select * from freebase_types where ");
			for(int i =0; i < 50; i++){
				bigTypeQueryBuilder.append("guid=? OR ");
			}
			bigTypeQueryBuilder.setLength(bigTypeQueryBuilder.length()-4);
			bigTypeQuery = conn
					.prepareStatement(bigTypeQueryBuilder.toString());
			
			initMapping();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static  void close() {
		try {
			guidQuery.close();
			typeQuery.close();
			bigTypeQuery.close();
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * return a list of FIGER types for the entityName or null if the
	 * entityName string is missing in the database.
	 * 
	 * @param entityName: space separated
	 * @return
	 */
	public static Set<String> getFigerTypes(String entityName) {
		Set<String> fbTypes = getFreebaseTypes(entityName);
		if (fbTypes == null) {
			return null;
		}
		Set<String> types = new HashSet<String>();
		for (String fbType : fbTypes) {
			String figerType = mapToFigerType(fbType);
			if (figerType != null) {
				types.add(figerType);
			}
		}
		return types;
	}
	
	/**
	 * return a list of FIGER types for the entityID or null if the
	 * entityID string is missing in the database.
	 * 
	 * @param entityName: space separated
	 * @return
	 */
	public static Set<String> getFigerTypesFromID(String entityID) {
		Set<String> fbTypes = getFreebaseTypesFromID(entityID);
		if (fbTypes == null) {
			return null;
		}
		Set<String> types = new HashSet<String>();
		for (String fbType : fbTypes) {
			String figerType = mapToFigerType(fbType);
			if (figerType != null) {
				types.add(figerType);
			}
		}
		return types;
	}

	/**
	 * return a list of Freebase types for the entityName or null if the
	 * entityName string is missing in the database.
	 * 
	 * @param entityName: space separated
	 * @return
	 */
	public  static Set<String> getFreebaseTypes(String entityName) {
		Set<String> types = new HashSet<String>();
		try {
			guidQuery.setString(1, entityName);
			ResultSet rs = guidQuery.executeQuery();
			String guid = null;
			if (rs.next()) {
				guid = rs.getString(1);
				rs.close();
			} else {
				// entityName not found!
				rs.close();
				return null;
			}
			typeQuery.setString(1, guid);
			rs = typeQuery.executeQuery();
			while (rs.next()) {
				types.add(rs.getString(1));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return types;
	}
	
	/**
	 * return a list of Freebase types for the entityID or null if the
	 * entityID string is missing in the database.
	 * 
	 * @param entityName: space separated
	 * @return
	 */
	public  static Set<String> getFreebaseTypesFromID(String entityID) {
		Set<String> types = new HashSet<String>();
		try {
			typeQuery.setString(1, entityID);
			ResultSet rs = typeQuery.executeQuery();
			while (rs.next()) {
				types.add(rs.getString(1));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return types;
	}

	/**
	 * It returns a FIGER type for the given Freebase type or a null if there is
	 * no mapping (i.e. the Freebase type should probably be discarded anyways).
	 * 
	 * @param freebaseType
	 * @return
	 */
	private static String mapToFigerType(String freebaseType) {
		if (mapping.containsKey(freebaseType)) {
			return mapping.get(freebaseType);
		} else {
			return null;
		}
	}
	
	public static boolean isConnected(){
		if(conn == null){
			return false;
		}
		else{
			return true;
		}
	}

	public static Map<String, Set<String>> bigQuery(Set<String> ids) throws SQLException {

		Set<String> ids50 = new HashSet<String>();
		Map<String,Set<String>> documentIdTypeMap = new HashMap<String,Set<String>>();
		
		int count =0;
		for(String id: ids){
			ids50.add(id);
			count++;
			if(count % 50 ==0){
				process50ids(ids50,documentIdTypeMap);
				ids50 = new HashSet<String>();
			}
		}
		if(ids50.size() > 0) process50ids(ids50,documentIdTypeMap);

		return documentIdTypeMap;
	}
	
	private static void process50ids(Set<String> idSet, Map<String,Set<String>> documentIdTypeMap) throws SQLException{
		//process 1000ids
		int j =1;
		String lastId = null;
		for(String queryId: idSet){
			lastId = queryId;
			bigTypeQuery.setString(j, GuidMidConversion.convertBackward(queryId));
			j++;
		}
		if(j != 50){
			while(j <= 50){
				bigTypeQuery.setString(j,GuidMidConversion.convertBackward(lastId));
				j++;
			}
		}
		ResultSet rs = bigTypeQuery.executeQuery();
		Map<String,Set<String>> guidToFbTypesMap = new HashMap<String,Set<String>>();
		while(rs.next()){
			String rowId = rs.getString(1);
			String fbType = rs.getString(2);
			if(guidToFbTypesMap.containsKey(rowId)){
				guidToFbTypesMap.get(rowId).add(fbType);
			}
			else{
				Set<String> fbTypes = new HashSet<String>();
				fbTypes.add(fbType);
				guidToFbTypesMap.put(rowId, fbTypes);
			}
		}
		for(String guid: guidToFbTypesMap.keySet()){
			if(!documentIdTypeMap.containsKey(guid)){
				Set<String> figerTypes = new HashSet<String>();
				Set<String> fbTypes = guidToFbTypesMap.get(guid);
				for(String fbType: fbTypes){
					String figerType = mapToFigerType(fbType);
					if (figerType != null) {
					//	System.out.println(fbType + " = " + figerType);
						figerTypes.add(figerType);
					}
				}
				documentIdTypeMap.put(guid,figerTypes);
			}
		}
	}

	public static void addFigerAnnotationToDocument(Annotation d) throws SQLException {
		
		List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
		Set<String> entityIds = new HashSet<String>();
		for(CoreMap sen: sentences){
			List<Triple<Pair<Integer,Integer>,String,Float>> nelAnnotation = sen.get(NamedEntityLinkingAnnotation.class);
			for(Triple<Pair<Integer,Integer>,String,Float> t: nelAnnotation){
				String id = t.second;
				if(!id.equals("null")){
					entityIds.add(id);
				}
			}
		}
		Map<String,Set<String>> idTypeMap = bigQuery(entityIds);
		//add type onto sentences
		for(CoreMap sen: sentences){
			List<Triple<Pair<Integer,Integer>,String,Float>> nelAnnotation = sen.get(NamedEntityLinkingAnnotation.class);
			List<Triple<Set<String>,Integer,Integer>> figerData = new ArrayList<>();
			for(Triple<Pair<Integer,Integer>,String,Float> t: nelAnnotation){
				Integer start = t.first.first;
				Integer end = t.first.second;
				Set<String> types = null;
				if(!t.second.equals("null")){
					types = idTypeMap.get(GuidMidConversion.convertBackward(t.second));
				}
				Triple<Set<String>,Integer,Integer> figerTrip = new Triple<>(types,start,end);
				figerData.add(figerTrip);
			}
			sen.set(FigerAnnotation.class, figerData);
		}
	}
	
	public static final class FigerAnnotation implements CoreAnnotation<List<Triple<Set<String>,Integer,Integer>>>{
		@Override
		public Class<List<Triple<Set<String>, Integer, Integer>>> getType() {
			return ErasureUtils.uncheckedCast(List.class);	
		}
		
	}
	
	public static Set<String> getFigerTypesFromFBType(String fbType){

		String figerType = "O";
		// initialize the freebase-figer type mapping
		if (mapping == null) {
			initMapping();
		}
		figerType = mapToFigerType(fbType);

		return generalizeFigerType(figerType);
	}
	
	private static Set<String> generalizeFigerType(String figerType) {
		Set<String> figerTypes = new HashSet<String>();
		if(figerType == null){
			figerTypes.add("O");
		}
		else{
			figerTypes.add(figerType);
			String[] typePath = figerType.split("/");
			if(typePath.length == 3){
				if(!typePath[1].equals(typePath[2])){
					String generalFigerType = "/"+typePath[1];
					figerTypes.add(generalFigerType);
				}
			}
		}
		return figerTypes;
	}

	private static void initMapping(){
		mapping = new Hashtable<String, String>();
		Scanner scanner = new Scanner(
				FigerTypeUtils.class.getResourceAsStream(typeFile),
				"UTF-8");
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			String arg = line.substring(0, line.indexOf("\t")), newType = line
					.substring(line.indexOf("\t") + 1).trim()
					.replace("\t", "/");
			mapping.put(arg, newType);
		}
		scanner.close();
	}
	
	public static Set<String> getFigerTypes(KBArgument a,
			List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData, List<CoreLabel> tokens) {

		Set<String> emptySet = new HashSet<String>();
		try{
		for(Triple<Pair<Integer,Integer>,String,String> notableTypeTrip : notableTypeData){
			if(tokens.get(notableTypeTrip.first.first).get(SentenceRelativeCharacterOffsetBeginAnnotation.class).equals(a.getStartOffset()) 
					&& 
			   tokens.get(notableTypeTrip.first.second-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class).equals(a.getEndOffset())
			   		&&
			   	a.getKbId().equals(notableTypeTrip.third)){
				return getFigerTypesFromFBType(notableTypeTrip.second);
			}
		}
		}
		catch(IndexOutOfBoundsException e){
			
		}
		
		return emptySet;
	}
	
}
