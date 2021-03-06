import java.util.*;
import java.io.*;
import java.lang.Math;
import java.util.Scanner;
import java.util.regex.*;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.htree.HTree;

public class Querier
{
	private RecordManager recman;

	public Database database;
	private StopStem stopStem;
	public HTree rank;
	private Vector<String> allWords;


	private static final int TOP_K_RESULTS = 50;
	private static final int MAX_KEYWORD_NUM = 5;
	private static final int NEAREST_WORD_TOLERATE_ABOVE = 3; // nearest word's length can be 3 units longer
	private static final int NEAREST_WORD_TOLERATE_BELOW = 3; // nearest word's length can be 3 units shorter
	private static final double S_WEIGHT = 1.0;
	private static final double TITLE_FAVOR_WEIGHT = 2.0;

	Querier() throws Exception
	{
		database = new Database();
		stopStem = new StopStem();
		allWords = database.wordMapTable.getAll(true);
		recman = RecordManagerFactory.createRecordManager("indexDB");
		rank = LoadOrCreate("rank");
	}

	private HTree LoadOrCreate(String hashtable_name) throws IOException
	{
		long recid = recman.getNamedObject(hashtable_name);
		if(recid != 0)
		{
			System.out.println("Hashtable found, id: " + recid);
			return HTree.load(recman, recid);
		}
		else
		{
			HTree hashtable = HTree.createInstance(recman);
			recid = hashtable.getRecid();
			recman.setNamedObject(hashtable_name, recid);
			System.out.println("Hashtable not found, new id: " + recid);
			return hashtable;
		}
	}

	public double idf(int word_id) throws Exception
	{
		int N = database.urlMapTable.getMaxId();
		int df = database.invertedIndex.getAllEntriesId(word_id).size();
		return (Math.log(N) - Math.log(df)) / Math.log(2.0);
	}

	public static void printlnWithLabel(String label, String text) throws Exception
	{
		System.out.println(label + ": " + text);
	}

	public static void printlnWithLabel(String label, int num) throws Exception
	{
		System.out.println(label + ": " + String.valueOf(num));
	}

	public static void printlnWithLabel(String label, double num) throws Exception
	{
		System.out.println(label + ": " + String.valueOf(num));
	}

	public static void printlnWithLabelWPair(String label, Vector<WPair> vec) throws Exception
	{
		if(vec != null){
			System.out.print(label + ": ");
			for (WPair wp : vec){
				System.out.print(wp.Key + "(" + String.valueOf(wp.Value) + ") ");
			}
			System.out.println();
		}
		else
			System.out.println(label + ": " + "none");
	}

	public static void printlnWithLabelFPair(String label, Vector<FPair> vec) throws Exception
	{
		if(vec != null){
			System.out.print(label + ": ");
			for (FPair fp : vec){
				System.out.print(String.valueOf(fp.Key) + "(" + String.valueOf(fp.Value) + ") ");
			}
			System.out.println();
		}
		else
			System.out.println(label + ": " + "none");
	}

	public static void printlnWithLabel(String label, Vector<String> vec) throws Exception
	{
		if(vec != null)
			System.out.println(label + ": " + vec.toString());
		else
			System.out.println(label + ": " + "none");
	}

	// Custom Sort
	public void sort(Vector<Pair> plist, boolean forward) {
	    Collections.sort(plist, new Comparator<Pair>() {
	        @Override
	        public int compare(Pair o1, Pair o2) {
	        	if (forward)
	            	return (o1.Value < o2.Value)?-1:(o1.Value > o2.Value)?1:0;
	            else
	            	return (o1.Value < o2.Value)?1:(o1.Value > o2.Value)?-1:0;
	        }
	    });
	}

	public boolean HasSequence(Vector<FPair> query, Vector<FPair> words) throws Exception
	{
		int index = 0;

		for(int i = 0; i < query.size(); i++)
		{
			//Get word id for the next query word
			int qword = query.get(i).Key;

			//Searche in the remaining document word list to see if the next query word is there
			//If it is, do the remaining searching from there
			int pos;
			for(pos = index + 1; pos < words.size(); pos++)
				if(qword == words.get(pos).Key)
				{
					index = pos;
					break;
				}
			//the next query word does not exist, so there is no exact phrase match
			if(pos == words.size())
				return false;
		}

		return true;
	}

	public double CosSim(Vector<FPair> s1, Vector<FPair> s2) throws Exception
	{
		if(s1.size() == 0.0 || s2.size() == 0.0)
			return 0.0;

		double score = 0;
		for(int i = 0; i < s1.size(); i++)
			for(int j = 0; j < s2.size(); j++)
				if(s1.get(i).Key == s2.get(j).Key)
					score += s1.get(i).Value * s2.get(j).Value;

		//System.out.println(score);

		double dist_s1 = 0.0;
		for(int i = 0; i < s1.size(); i++)
			dist_s1 += Math.pow(s1.get(i).Value, 2);

		double dist_s2 = 0.0;
		for(int j = 0; j < s2.size(); j++)
			dist_s2 += Math.pow(s2.get(j).Value, 2);

		dist_s1 = Math.sqrt(dist_s1);
		dist_s2 = Math.sqrt(dist_s2);

		return score / dist_s1 / dist_s2;
	}

	public double QCosSim(Vector<FPair> s1, Vector<FPair> s2) throws Exception
	{
		if(s1.size() == 0.0 || s2.size() == 0.0)
			return 0.0;

		double score = 0;
		if(HasSequence(s1, s2))
			for(int i = 0; i < s1.size(); i++)
				score += Math.pow(s1.get(i).Value, 2);

		double dist_s1 = 0.0;
		for(int i = 0; i < s1.size(); i++)
			dist_s1 += Math.pow(s1.get(i).Value, 2);

		double dist_s2 = 0.0;
		for(int j = 0; j < s2.size(); j++)
			dist_s2 += Math.pow(s2.get(j).Value, 2);

		dist_s1 = Math.sqrt(dist_s1);
		dist_s2 = Math.sqrt(dist_s2);

		return score / dist_s1 / dist_s2;
	}

	public Vector<FPair> QueryWeight(String query) throws Exception
	{
		Vector<FPair> query_weight = new Vector<FPair>();

		//split and filter query string to vector space
		String[] s_query = query.replaceAll("[^\\w\\s]|_", "").trim().toLowerCase().split(" ");

		//filter stopwords and stem for normal and qouted queries
		Vector<String> p_query = stopStem.stopAndStem(s_query);

		System.out.print("Stemmed query: ");
		for(String s : p_query)
			System.out.print(s + " ");
		System.out.println("");

		//convert words to word_ids, skip if not found in database
		Vector<Integer> query_id = database.wordMapTable.valueToKey(p_query);

		//create weight for query
		HashSet<Integer> unique_id = new HashSet<Integer>(query_id);
		for(int id : unique_id)
			query_weight.add(new FPair(id, 1.0));//Collections.frequency(query_id, id) * idf(id))); <-- Can change to tf-idf anytime

		return query_weight;
	}

	public Vector<Vector<FPair>> QuoteWeight(String query) throws Exception
	{
		//Vector of Vector since there might be more than 1 quote
		Vector<Vector<FPair>> query_weight = new Vector<Vector<FPair>>();

		//Find substring inside " " and extract them
		Pattern pattern = Pattern.compile("\"(.*?)\"");
		Matcher matcher = pattern.matcher(query);
		Vector<String[]> quote = new Vector<String[]>();
		while(matcher.find())
			quote.add(matcher.group(1).replaceAll("[^\\w\\s]|_", "").trim().toLowerCase().split(" "));

		//Stem list of strings in each quote
		Vector<Vector<String>> q_query = new Vector<Vector<String>>();
		for(String[] q : quote)
			q_query.add(stopStem.stopAndStem(q));

		System.out.print("Quoted query: ");
		for(Vector<String> q : q_query)
		{
			for(String s : q)
				System.out.print(s + " ");
			System.out.print("\t");
		}
		System.out.println("");

		//Convert list of strings to list of word_id for each quote
		Vector<Vector<Integer>> query_id = new Vector<Vector<Integer>>();
		for(Vector<String> q : q_query)
			query_id.add(database.wordMapTable.valueToKey(q));

		//create weight vector for each quote
		for(Vector<Integer> id : query_id)
		{
			Vector<FPair> weight = new Vector<FPair>();
			HashSet<Integer> unique_id = new HashSet<Integer>(id);
			for(int u_id : unique_id)
				weight.add(new FPair(u_id, 1.0));//Collections.frequency(query_id, id) * idf(id))); <-- Can change to tf-idf anytime
			query_weight.add(weight);
		}

		return query_weight;
	}

	// The tfidf of the document
	public Vector<FPair> DocWeight(int doc_id) throws Exception
	{
		//Get all words of a document
		return database.vsmIndex.getAllEntriesVSM(doc_id);
	}

	// The title's tf of the document
	public Vector<FPair> TitleWeight(int doc_id) throws Exception
	{
		//Get all title of a document
		return database.titleVsmIndex.getAllEntriesVSM(doc_id);
	}

	public String findNearestWord(String word) throws Exception
	{
		//System.out.println("This word to be found for: " + word);

		int minDistance = 100;
		String nearestWord = "NA";
		int len_of_word = word.length();

		for (String candidate_word : allWords){
			int len_of_candidate = candidate_word.length();

			int levDistance = Levenshtein.distance(word, candidate_word);
			if(levDistance < minDistance){
				minDistance = levDistance;
				nearestWord = candidate_word;
			}
		}

		return nearestWord;
	}


	public String querySuggestion(String query) throws Exception
	{
		//split and filter query string to vector space
		String[] word_array = query.replaceAll("[^\\w\\s]|_", "").trim().toLowerCase().split(" ");

		Vector<String> suggest_query_vector = new Vector<String>();

		for (String word : word_array){
			if(stopStem.isStopWord(word)){
				suggest_query_vector.add(word);
			} else {

				String stem_word = stopStem.stem(word);

				int validity = database.wordMapTable.getKey(stem_word);

				if(validity == -1){
					suggest_query_vector.add(findNearestWord(word));
				} else {
					suggest_query_vector.add(word);
				}

			}
		}

		String result = "";
		for (String word : suggest_query_vector){
			result += word + " ";
		}
		//result.substring(result.length()-2);
		//String result = suggest_query_vector.toString();

		return result;
	}

	public String Str(int value)
	{
		return Integer.toString(value);
	}

	// TODO: add pagerank
	public SearchResult NaiveSearch(String query, Integer topK, double similairty_w) throws Exception
	{
		//Converts query into VSM of weights
		// "normal unquoted" & "quoted"
		Vector<FPair> n_query_weight = QueryWeight(query);
		Vector<Vector<FPair>> q_query_weight = QuoteWeight(query);

		//Iterate through all webpages
		int max_doc = database.urlMapTable.getMaxId();

		Vector<FPair> scores = new Vector<FPair>();
		Vector<FPair> PR = new Vector<FPair>();
		for(int i = 0; i < max_doc; i++)
		{
			//Get tf-idf vector of a document
			Vector<FPair> doc_weight = DocWeight(i);
			//if it doesn't exist, then the document is not crawled
			if(doc_weight == null)
				continue;


			//Summation of normal query score and quoted query score
			double doc_score = CosSim(n_query_weight, doc_weight);
			for(Vector<FPair> query_weight : q_query_weight)
				doc_score += QCosSim(query_weight, doc_weight);

			//System.out.println(String.valueOf(i) + ": " + String.valueOf(doc_score));




			// TODO: favor title
			Vector<FPair> title_weight = TitleWeight(i);
			//printlnWithLabelFPair("title_weight", title_weight);

			double title_score = CosSim(n_query_weight, title_weight);
			for(Vector<FPair> query_weight : q_query_weight)
				title_score += QCosSim(query_weight, title_weight);

			//System.out.println(String.valueOf(i) + ": " + String.valueOf(title_score));
			/*
			if(title_score > 0){
				System.out.println("This work! " + String.valueOf(i));
			}
			*/


			Double score = doc_score + title_score * TITLE_FAVOR_WEIGHT;
			String skey = Str(i);
			Double pagerank = (Double)rank.get(skey);
			PR.add(new FPair(i, pagerank));

			scores.add(new FPair(i, score));
		}


		// All search results in FPAir format
		Vector<FPair> list = FPair.TopK(scores, topK, PR, similairty_w);
		// All search results
		Vector<PageInfo> results = new Vector<PageInfo>();

		// Create PageInfo for each page
		for(FPair p : list){
			// Single search result
			PageInfo result = new PageInfo();
			// Get metadata
			Vector<String> resultMeta = database.metaIndex.getAllEntriesMeta(p.Key);
			// Get keyword pairs
			Vector<Pair> resultKeywordFreq = database.forwardIndex.getAllEntriesId(p.Key);

			// Sort the keywords by frequency, from largest to lowest(false)
			sort(resultKeywordFreq, false);

			// Avoid error when key word list length < 5
			int max_keyarray_length = (resultKeywordFreq.size() > MAX_KEYWORD_NUM)? MAX_KEYWORD_NUM : resultKeywordFreq.size();


			for(int j = 0; j < max_keyarray_length; j++){
				WPair keywordPair = new WPair(database.wordMapTable.getEntry(resultKeywordFreq.get(j).Key),
					resultKeywordFreq.get(j).Value);
				result.KeywordVector.add(keywordPair);
			}

			// Get title, url, date and size
			result.Title = resultMeta.get(0);
			result.Url = database.urlMapTable.getEntry(p.Key);
			result.LastModifiedDate = resultMeta.get(1);
			result.SizeOfPage = Integer.parseInt(resultMeta.get(2));

			// Get child links
			Vector<String> childLinkVecID = database.linkIndex.getAllEntriesChildLink(p.Key);

			if(childLinkVecID == null){
				result.ChildLinkVector.add("N/A");
			} else {
				for (String id : childLinkVecID){
					result.ChildLinkVector.add(database.urlMapTable.getEntry(Integer.parseInt(id)));
				}
			}

			// Get parent links
			Vector<String> parentLinkVecID = database.linkIndex.getAllEntriesParentLink(p.Key);

			if(parentLinkVecID == null){
				result.ParentLinkVector.add("N/A");
			} else {
				for (String id : parentLinkVecID){
					result.ParentLinkVector.add(database.urlMapTable.getEntry(Integer.parseInt(id)));
				}
			}


			// Store score
			result.Score = p.Value;

			results.add(result);
		}


		// Get suggested query
		String suggestedQuery = querySuggestion(query);

		SearchResult searchResults = new SearchResult(suggestedQuery, results);

		return searchResults;
	}

	//Prints all websites containing any of the query words
	public void WordInDoc(String query) throws Exception
	{
		//split and filter query string to vector space
		String[] s_query = query.replaceAll("[^\\w\\s]|_", "").trim().toLowerCase().split(" ");

		//filter stopwords and stem
		Vector<String> p_query = stopStem.stopAndStem(s_query);

		//convert words to word_ids, skip if not found in database
		Vector<Integer> query_id = database.wordMapTable.valueToKey(p_query);

		//distinct()
		HashSet<Integer> unique_id = new HashSet<Integer>(query_id);
		for(int id : unique_id)
		{
			System.out.println(database.wordMapTable.getEntry(id) + ": ");

			Vector<Pair> entries = database.invertedIndex.getAllEntriesId(id);
			for(Pair entry : entries)
				System.out.println(database.urlMapTable.getEntry(entry.Key));

			System.out.println("\n\n");
		}
	}

	public static void main(String[] args)
	{
		try
		{
			Querier querier = new Querier();
			Scanner scanner = new Scanner(System.in);

			int top_k = TOP_K_RESULTS;

			double similarity_w = S_WEIGHT;

			if(args.length > 0)
				similarity_w = Double.parseDouble(args[0]);

			while(true)
			{
				System.out.print("\nSearch for: ");
				String query = scanner.nextLine();
				String query_space = query.replaceAll("\\s+","");

				if(query_space.equals(""))
					continue;

				if(query.equals("quit"))
					break;

				// Enter parameters
				System.out.print("\nNumber of results to return: ");
				top_k = Integer.parseInt(scanner.nextLine());
				System.out.print("\nCosine similarity weight: ");
				similarity_w = Double.parseDouble(scanner.nextLine());


				// Print searching result by PageInfo
				long tStart = System.currentTimeMillis();
				SearchResult searchResult = querier.NaiveSearch(query, top_k, similarity_w);
				String suggestedQuery = searchResult.SuggestedQuery;
				System.out.println("\nSuggested Query: " + suggestedQuery);

				System.out.println("Displaying Top-" + top_k + " results");
				System.out.println("\nSearch Result:");

				for(PageInfo doc : searchResult.PageInfoVector){
					printlnWithLabel("Title", doc.Title);
					printlnWithLabel("Url", doc.Url);
					printlnWithLabel("Last Modified Date", doc.LastModifiedDate);
					printlnWithLabel("Size of Page", doc.SizeOfPage);
					printlnWithLabel("Score", doc.Score);
					printlnWithLabelWPair("Keywords", doc.KeywordVector);
					printlnWithLabel("Child Links", doc.ChildLinkVector);
					printlnWithLabel("Parent Links", doc.ParentLinkVector);
					System.out.println("---------------------------------------------------------------");
				}

				long tEnd = System.currentTimeMillis();
				long tDelta = tEnd - tStart;
				double elapsedSeconds = tDelta / 1000.0;
				System.out.println("elasped time (sec): "+elapsedSeconds);
			}
		}
		catch (Exception e)
		{
			System.err.println("Error1");
			System.err.println(e.toString());
		}
	}
}
