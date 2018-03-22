package org.fiz.ise.gwifi.dataset.shorttext.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.fiz.ise.gwifi.Singleton.AnnotationSingleton;
import org.fiz.ise.gwifi.Singleton.CategorySingleton;
import org.fiz.ise.gwifi.Singleton.WikipediaSingleton;
import org.fiz.ise.gwifi.dataset.LINE.Category.Categories;
import org.fiz.ise.gwifi.model.Model_LINE;
import org.fiz.ise.gwifi.model.TestDatasetType_Enum;
import org.fiz.ise.gwifi.util.Config;
import org.fiz.ise.gwifi.util.FileUtil;
import org.fiz.ise.gwifi.util.MapUtil;
import org.fiz.ise.gwifi.util.Print;

import edu.kit.aifb.gwifi.annotation.Annotation;
import edu.kit.aifb.gwifi.hsa.Test;
import edu.kit.aifb.gwifi.model.Article;
import edu.kit.aifb.gwifi.model.Category;
import edu.kit.aifb.gwifi.model.Page;
import edu.kit.aifb.gwifi.model.Wikipedia;
import edu.kit.aifb.gwifi.model.Page.PageType;
import edu.kit.aifb.gwifi.service.NLPAnnotationService;
import edu.kit.aifb.gwifi.util.PageIterator;
import jeigen.TestDenseAggregator;

public class HeuristicApproach {

	private final static TestDatasetType_Enum TEST_DATASET_TYPE= Config.getEnum("TEST_DATASET_TYPE");
	private static WikipediaSingleton wikiSing = WikipediaSingleton.getInstance();
	private static Wikipedia wikipedia = wikiSing.wikipedia;
	private static CategorySingleton singCategory= CategorySingleton.getInstance(Categories.getCategoryList(TEST_DATASET_TYPE));
	private static Set<Category> setMainCategories = new HashSet<>(singCategory.setMainCategories);
	private static Set<Category> setAllCategories = new HashSet<>(singCategory.setAllCategories);
	private static Map<Category, Set<Category>> mapCategories = new HashMap<>(singCategory.map);

	private static final Logger LOG = Logger.getLogger(AnalyseDataset_websnippets.class);
	static final Logger secondLOG = Logger.getLogger("debugLogger");
	/*
	 * The main purpose of this class is the calculate the similarity and decide 
	 * which category a text belongs to based on the probability
	 * 
	 */
	public static Category getBestMatchingCategory(String shortText,List<Category> gtList) {
		List<Annotation> lstAnnotations = new ArrayList<>();
		AnnotationSingleton singleton = AnnotationSingleton.getInstance();
		NLPAnnotationService service = singleton.service;
		StringBuilder mainBuilder = new StringBuilder();
		try {
			Map<Category, Double> mapScore = new HashMap<>(); 
			Map<Category, Double> mapScoreNoPopularity = new HashMap<>(); 
			mainBuilder.append(shortText+"\n");
			StringBuilder strBuild = new StringBuilder();
			for(Category c: gtList)	{
				strBuild.append(c+" ");
			}
			mainBuilder.append(strBuild.toString()+"\n"+"\n");
			service.annotate(shortText, lstAnnotations);
			boolean first =true;
			for (Category mainCat : setMainCategories) {
				//secondLOG.info(mainCat);
				double score = 0.0; 
				for(Annotation a:lstAnnotations) {
					List<Annotation> contextAnnotation = lstAnnotations.stream()
							.filter(p -> p.getId()!=a.getId()).collect(Collectors.toList());
					double P_e_c=get_P_e_c(a.getId(), mainCat);
					if (first) {
						mainBuilder.append(a.getMention()+", "+a.getTitle()+", most similar cat:"+EmbeddingsService.getMostSimilarCategory(a, Model_LINE.LINE_COMBINED).getTitle()+", "+a.getWeight()+"\n");
					}
					//double P_Se_c=get_P_Se_c(a);
					//System.out.println(mainCat+" "+P_Se_c);
//					double P_Ce_e=get_P_Ce_e(a.getId(),contextAnnotation);
//					score+=(P_e_c*P_Se_c*P_Ce_e);
//					score+=(P_e_c*P_Ce_e);
					score+=(P_e_c);
				}
				first=false;
				double P_c=get_P_c_(mainCat);
				score=score/lstAnnotations.size();
				mapScoreNoPopularity.put(mainCat, score);
				score*=P_c;
				mapScore.put(mainCat, score);
			}
			mainBuilder.append("\n");
			Map<Category, Double> sortedMap = new LinkedHashMap<>(MapUtil.sortByValueDescending(mapScore));
			for(Entry<Category, Double> e: sortedMap.entrySet()){			
				mainBuilder.append(e.getKey()+" "+e.getValue()+"\n");
			}
			sortedMap = new LinkedHashMap<>(MapUtil.sortByValueDescending(mapScoreNoPopularity));
			Category firstElement = MapUtil.getFirst(sortedMap).getKey();
			mainBuilder.append("\n" +"without popularity"+"\n");
			//Print.printMap(sortedMap);
			for(Entry<Category, Double> e: sortedMap.entrySet()){			
				mainBuilder.append(e.getKey()+" "+e.getValue()+"\n");
			}
			mainBuilder.append(""+"\n");
			mainBuilder.append(""+"\n");
			//System.out.println(mainBuilder.toString());
			secondLOG.info(mainBuilder.toString());
			return firstElement;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/*
	 * Popularity of the category
	 */
	private static int get_P_c_(Category c){
		return c.getChildArticles().length;
	}
	private static double get_P_e_c(int articleID,Category mainCat) {
		Set<Category> childCategories = new HashSet<>(mapCategories.get(mainCat));
		double result =0.0;
		double countNonZero=0;
		for(Category c:childCategories) {
			double P_Cr_c = EmbeddingsService.getSimilarity(String.valueOf(mainCat.getId()), String.valueOf(c.getId()));
			double P_e_Cr =EmbeddingsService.getSimilarity(String.valueOf(articleID), String.valueOf(c.getId()));
			double temp =P_e_Cr*P_Cr_c;
			if (!Double.isNaN(temp)&&temp>0.0) {
				result+=temp;
				countNonZero++;
			}
			else{
				LOG.info("similarity could not be calculated category: "+c.getTitle()+" "+c.getChildArticles().length);
			}
			
		}
		return result/countNonZero;
	}

	private static double get_P_Se_c(Annotation a) {//comes from EL system weight value because we calculate the confidence based on the prior prob
		return a.getWeight();
	}

	/**
	 * This method takes the all the context entities and tries to calculate the probabilities of the given an 
	 * entitiy and all the other context entities and sums them up
	 * @return
	 */
	private static double get_P_Ce_e(Integer mainId,List<Annotation> contextEntities){ //Context entities an the entity(already disambiguated) 
		double result =0.0;
		for(Annotation a: contextEntities){
			double temp =(EmbeddingsService.getSimilarity(String.valueOf(mainId), String.valueOf(a.getId())));
			double countNonZero=0;
			if (!Double.isNaN(temp)&&temp>0.0) {
				countNonZero++;
				result+=temp;
			}
			else{
				LOG.info("similarity could not be calculated entity-entity: "+mainId+" "+a.getURL());
			}
		}
		return result;
	}
}
