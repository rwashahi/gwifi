package org.fiz.ise.gwifi.test.afterESWC;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleBiFunction;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.deeplearning4j.parallelism.ConcurrentHashSet;
import org.fiz.ise.gwifi.Singleton.AnnotationSingleton;
import org.fiz.ise.gwifi.Singleton.WikipediaSingleton;
import org.fiz.ise.gwifi.dataset.test.ReadDataset;
import org.fiz.ise.gwifi.model.AG_DataType;
import org.fiz.ise.gwifi.util.AnnonatationUtil;
import org.fiz.ise.gwifi.util.Config;
import org.fiz.ise.gwifi.util.FileUtil;
import org.fiz.ise.gwifi.util.MapUtil;
import org.fiz.ise.gwifi.util.SynchronizedCounter;
import org.fiz.ise.gwifi.util.TimeUtil;

import com.hp.hpl.jena.sparql.pfunction.library.listIndex;

import edu.kit.aifb.gwifi.annotation.Annotation;
import edu.kit.aifb.gwifi.model.Category;

public class GenerateWideFeatureSet {
	private static final String DATASET_TEST_AG = Config.getString("DATASET_TEST_AG","");
	private static final String DATASET_TRAIN_AG = Config.getString("DATASET_TRAIN_AG","");
	static final Logger secondLOG = Logger.getLogger("debugLogger");
	static final Logger resultLog = Logger.getLogger("reportsLogger");
	private static ExecutorService executor;
	private final static Integer NUMBER_OF_THREADS= Config.getInt("NUMBER_OF_THREADS",-1);
	static Map<String, Integer> mapAllThePairs = new ConcurrentHashMap<String, Integer>();
	static Map<String, Integer> mapAllEntities = new ConcurrentHashMap<String, Integer>();
	static Set<Integer> setAllEntities = new ConcurrentHashSet<Integer>();
	private static SynchronizedCounter synCountNumberOfEntityPairs;
	static final Integer i_filter = 100;

	public static void main(String[] args) throws Exception {

		synCountNumberOfEntityPairs= new SynchronizedCounter();
		AnnotationSingleton.getInstance();
		List<String> lstTrainDataset = new ArrayList<String>(ReadDataset.read_AG_BasedOnType(DATASET_TRAIN_AG, AG_DataType.TITLEANDDESCRIPTION));
		List<String> lstTestDataset = new ArrayList<String>(ReadDataset.read_AG_BasedOnType(DATASET_TEST_AG, AG_DataType.TITLEANDDESCRIPTION));

		List<String> lstCombined = new ArrayList<String>(lstTrainDataset);
		lstCombined.addAll(lstTestDataset);
		//		secondLOG.info("Size of the dataset: "+lstCombined.size());
		//		System.out.println("Size of the dataset: "+lstCombined.size());

		//		List<Annotation> lstAllAnnotation = new ArrayList<Annotation>(AnnonatationUtil.findAnnotationAll_FilterAG(lstCombined));
		//		System.out.println("Size of the anotation: "+lstAllAnnotation.size());
		//		secondLOG.info("Size of the anotation: "+lstAllAnnotation.size());
		//
		//		Set<Annotation> set = new HashSet<Annotation>(lstAllAnnotation);
		//		System.out.println("Size of the set" + set.size());
		//		secondLOG.info("Size of the set" + set.size());
		//Map<String, Integer> mapAllThePairs = findAllPossibleEntityPairs(lstCombined);

		System.out.println("Combined size data "+lstCombined.size());
		Long start =TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()) ;
		GenerateWideFeatureSet feature = new GenerateWideFeatureSet();

		//		feature.findAllPossibleEntityOccurance(lstCombined);
		//		FileUtil.writeDataToFile(setAllEntities, "AllEntities.txt");

		//feature.findAllPossibleEntityPairs(lstCombined);

		//		System.out.println("Total number of pairs: "+synCountNumberOfEntityPairs.value());
		//		System.out.println("Writing to a file...");
		//FileUtil.writeDataToFile(MapUtil.sortByValueAscending2(mapAllThePairs), "AllPairsWithFrequency.txt");


		mapAllThePairs=readDataFromFileForIndexingMap("WideFeatures/AllPairsWithFrequency_filteredNoise_sorted.txt","\t\t");
		System.out.println("Size of the map-all the possible pairs: "+mapAllThePairs.size());
		System.out.println("Seconds time : "+TimeUtil.getEnd(TimeUnit.MINUTES, start));
		System.out.println("Call find features...");
		feature.findFeaturesForEntityPairs_paralel(lstTestDataset);



		//***************+Features for single entities**********************
		//mapAllEntities=readDataFromFileForIndexingMapSingleEntity("AllEntities.txt");
		//		feature.findFeaturesForPossibleEntityOccurance_paralel(lstTrainDataset);
		//feature.findFeaturesForPossibleEntityOccurance_paralel(lstTestDataset);
	}


	private void findFeaturesForPossibleEntityOccurance_paralel(List<String> lstTrainDataset) throws InterruptedException {
		executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);		
		for (int i = 0; i < lstTrainDataset.size(); i++) {
			executor.execute(handleFeatureExtractionForEntity(lstTrainDataset.get(i),i));
		}
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

	}
	private Runnable handleFeatureExtractionForEntity(String str,int count)  {
		return () -> {
			List<Annotation> lstAnnotations = new ArrayList<>();
			try {
				AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);
				Set<Integer> setPairsIndex = new HashSet<Integer>();
				for(Annotation a : lstAnnotations) {
					setPairsIndex.add(mapAllEntities.get(a.getId()));
				}
				StringBuilder result = new StringBuilder(str+"\t");
				for (int i = 0; i < mapAllEntities.size(); i++) {
					if (setPairsIndex.contains(i)) {
						result.append(1+",");
					}
					else {
						result.append(0+",");

					}
				}
				resultLog.info(result.toString().subSequence(0, result.toString().length()-1));
				System.out.println("Number of Sentences processed: "+ count);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}//annotate the given text


		};
	}

	private void findFeaturesForEntityPairs_paralel(List<String> lstTrainDataset) throws Exception {
		executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);		
		for (int i = 0; i < lstTrainDataset.size(); i++) {
			executor.execute(handleFeatureExtractionForEntityPairs(lstTrainDataset.get(i),i));
		}
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

	}
	private Runnable handleFeatureExtractionForEntityPairs(String str,int count)  {
		return () -> {
			List<Annotation> lstAnnotations = new ArrayList<>();
			Set<String> setPairs = new HashSet<String>();
			try {
				AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);

				List<Integer> lstId = new ArrayList<Integer>();
				for(Annotation a : lstAnnotations) {
					if (!AnnonatationUtil.getEntityBlackList_AGNews().contains(a.getId())&&WikipediaSingleton.getInstance().getArticle(a.getTitle())!=null) {
						lstId.add(a.getId());
					}
				}
				Collections.sort(lstId); 

				for (int i = 0; i < lstId.size(); i++) {
					for (int j = i+1; j < lstId.size(); j++) {
						setPairs.add(lstId.get(i)+"\t"+lstId.get(j));
					}	
				}

				Set<Integer> setPairsIndex = new HashSet<Integer>();
				for(String s : setPairs) {
					if (mapAllThePairs.containsKey(s)) {
						setPairsIndex.add(mapAllThePairs.get(s));
					}
				}
				if (setPairsIndex.contains(null)) {
					System.out.println("setPairsIndex contains null: "+ count);
					System.exit(0);
				}

				StringBuilder result = new StringBuilder(str+"\t");

				for (int i: setPairsIndex) {
					result.append(i+",");
				}
				//				for (int i = 0; i < mapAllThePairs.size(); i++) {
				//					if (setPairsIndex.contains(i)) {
				//						result.append(1+",");
				//					}
				//					else {
				//						result.append(0+",");
				//
				//					}
				//				}
				//				if (result.toString().contains("null")) {
				//					
				//					System.out.println("str: "+ str+ ": "+str.length());
				//					System.out.println("contains null: "+ count);
				//					System.out.println("result: "+ result.toString().indexOf("null"));
				//					System.exit(0);
				//
				//				}
				if (setPairsIndex.isEmpty()) {
					resultLog.info(result.toString());
				}
				else {
					resultLog.info(result.toString().subSequence(0, result.toString().length()-1));
				}
				System.out.println("Number of Sentences processed: "+ count);
			} catch (Exception e) {

				System.out.println("handleFeatureExtractionForEntityPairs in." + setPairs);
				e.printStackTrace();
				System.exit(0);;
			}//annotate the given text


		};
	}


	private static void findFeaturesForEntityPairs(List<String> lstTrainDataset)
			throws Exception {

		System.out.println("Inside find features...");
		int count=0;
		for(String str : lstTrainDataset) {
			Long start = System.currentTimeMillis();
			List<Annotation> lstAnnotations = new ArrayList<>();
			Set<String> setPairs = new HashSet<String>();
			AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);//annotate the given text

			List<Integer> lstId = new ArrayList<Integer>();
			for(Annotation a : lstAnnotations) {
				lstId.add(a.getId());
			}
			Collections.sort(lstId); 

			for (int i = 0; i < lstId.size(); i++) {
				for (int j = i+1; j < lstId.size(); j++) {
					setPairs.add(lstId.get(i)+"\t"+lstId.get(j));
				}	
			}
			System.out.println(setPairs);

			System.out.println("After second for loop: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
			Set<Integer> setPairsIndex = new HashSet<Integer>();
			for(String s : setPairs) {
				setPairsIndex.add(mapAllThePairs.get(s));
			}
			System.out.println(setPairsIndex);

			StringBuilder result = new StringBuilder(str+"\t");
			start = System.currentTimeMillis();
			for (int i = 0; i < mapAllThePairs.size(); i++) {
				if (setPairsIndex.contains(i)) {
					result.append(1+",");
				}
				else {
					result.append(0+",");

				}
			}

			System.out.println("Final for loop for string generation: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
			resultLog.info(result);
			System.out.println("Number of Sentences processed: "+ count++);
			System.out.println("---------------------------------------------------------------------------");
		}
	}
	//	private static void findFeaturesForEntityPairs(List<String> lstTrainDataset)
	//			throws Exception {
	//
	//		System.out.println("Inside find features...");
	//		int count=0;
	//		for(String str : lstTrainDataset) {
	//			Long start = System.currentTimeMillis();
	//			Map<String, Integer> mapTrainFeatures = new LinkedHashMap<String, Integer>(mapAllThePairs);	
	//			System.out.println("To generate copy of the map: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
	//			start = System.currentTimeMillis();
	//			List<Annotation> lstAnnotations = new ArrayList<>();
	//			AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);//annotate the given text
	//			Collections.sort(lstAnnotations); 
	//			for (int i = 0; i < lstAnnotations.size(); i++) {
	//				for (int j = i+1; j < lstAnnotations.size(); j++) {
	//					//String key = lstAnnotations.get(i)+"\t"+lstAnnotations.get(j);
	//					mapTrainFeatures.put(lstAnnotations.get(i)+"\t"+lstAnnotations.get(j), 1);
	//				}	
	//			}
	//			System.out.println("After second for loop: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
	//			System.out.println("Finished assigning map: "+ mapTrainFeatures.size());
	//			start = System.currentTimeMillis();
	//			StringBuilder result = new StringBuilder(str+"\t");
	//			List<Integer> lst = new LinkedList<Integer>(mapTrainFeatures.values());
	//			//System.out.println(lst);
	//			System.out.println("Copy values of map to list: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
	//			start = System.currentTimeMillis();
	//			for(Integer i : lst) {
	//				result.append(i+",");
	//				//				result=result+i+",";
	//			}
	//			//			result = result.substring(0, result.length() - 1);
	//			System.out.println("Final for loop for string generation: "+TimeUtil.getEnd(TimeUnit.MILLISECONDS, start));
	//			resultLog.info(result);
	//			System.out.println("Number of Sentences processed: "+ count++);
	//		}
	//	}


	private void findAllPossibleEntityOccurance(List<String> lstCombined) throws Exception {
		executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
		for (int i = 0; i < lstCombined.size(); i++) {
			executor.execute(handle_findSingleEntityOccurance(lstCombined.get(i),i));
		}
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}
	private Runnable handle_findSingleEntityOccurance(String str,int count)  {
		return () -> {
			List<Annotation> lstAnnotations = new ArrayList<>();
			try {
				AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);
				for(Annotation a : lstAnnotations) {
					setAllEntities.add(a.getId());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}


	private void findAllPossibleEntityPairs(List<String> lstCombined) throws Exception {
		executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
		for (int i = 0; i < lstCombined.size(); i++) {
			executor.execute(handleEntitiyPairs(lstCombined.get(i),i));
		}
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

	}
	private Runnable handleEntitiyPairs(String str,int count)  {
		return () -> {
			List<Annotation> lstAnnotations = new ArrayList<>();
			try {
				AnnotationSingleton.getInstance().service.annotate(str, lstAnnotations);
				List<Integer> lstId = new ArrayList<Integer>();

				for(Annotation a : lstAnnotations) {
					if (!AnnonatationUtil.getEntityBlackList_AGNews().contains(a.getId())&&WikipediaSingleton.getInstance().getArticle(a.getTitle())!=null) {
						lstId.add(a.getId());
					}
				}
				Collections.sort(lstId); 
				for (int i = 0; i < lstId.size(); i++) {
					for (int j = i+1; j < lstId.size(); j++) {

						//if (lstId.get(i)!=lstId.get(j)) {
						String key = lstId.get(i)+"\t"+lstId.get(j);
						//mapAllThePairs.put(lstId.get(i)+"\t"+lstId.get(j), 0);
						
						synCountNumberOfEntityPairs.increment();
						mapAllThePairs.merge(key, 1, Integer::sum);

						//}

					}	
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}
	public static Map<Integer, Integer> readDataFromFileForIndexingMapSingleEntity(final String fileName) {
		Map<Integer, Integer> map = new LinkedHashMap<>();
		int index=0;
		try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				map.put(Integer.parseInt(line),index++);
			}
		} catch (IOException e) {
			System.err.format("IOException: %s%n", e);
		}
		System.out.println("Size of the map after reading "+map.size());
		return map;
	}
	public static Map<String, Integer> readDataFromFileForIndexingMap(final String fileName, String separator) {
		Map<String, Integer> map = new LinkedHashMap<>();
		int index=0;
		List<Integer> filter = new ArrayList<Integer>();
		for (int i = 1; i<=i_filter; i++) {
			filter.add(i);
		}
		try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] split = line.split(separator);
				if (!filter.contains(Integer.parseInt(split[1]))) {
					map.put(split[0],index);
					index=index+1;
				}
			}
		} catch (IOException e) {
			System.err.format("IOException: %s%n", e);
		}
		System.out.println("Size of the map after reading "+map.size());
		return map;
	}


}
