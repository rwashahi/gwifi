package org.fiz.ise.gwifi.Singleton;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fiz.ise.gwifi.dataset.LINE.Category.Categories;
import org.fiz.ise.gwifi.model.TestDatasetType_Enum;
import org.fiz.ise.gwifi.util.Config;

import edu.kit.aifb.gwifi.model.Article;
import edu.kit.aifb.gwifi.model.Category;

public class PageCategorySingleton {
	private static PageCategorySingleton single_instance = null;
	private final static TestDatasetType_Enum TEST_DATASET_TYPE = Config.getEnum("TEST_DATASET_TYPE");
	public Map<Category, Set<Article>> mapMainCatAndArticles;

	private PageCategorySingleton() {
		Set<Category> setMainCategories = new HashSet<>(
				CategorySingleton.getInstance(Categories.getCategoryList(TEST_DATASET_TYPE)).setMainCategories);
		mapMainCatAndArticles = new HashMap<>();
		for (Category c : setMainCategories) {
			Set<Category> childCategories = new HashSet<>(
					CategorySingleton.getInstance(Categories.getCategoryList(TEST_DATASET_TYPE)).mapMainCatAndSubCats
							.get(c));
			Set<Article> sArticle = new HashSet<>();
			sArticle.addAll(Arrays.asList(c.getChildArticles()));
			for (Category child : childCategories) {
				sArticle.addAll(Arrays.asList(child.getChildArticles()));
			}
			mapMainCatAndArticles.put(c, sArticle);
		}
	}

	public static PageCategorySingleton getInstance() {
		if (single_instance == null)
			single_instance = new PageCategorySingleton();
		return single_instance;
	}
}
