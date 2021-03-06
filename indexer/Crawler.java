import org.htmlparser.beans.StringBean;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.filters.HasAttributeFilter; // meta
import org.htmlparser.filters.TagNameFilter; // meta
import org.htmlparser.tags.LinkTag;
import org.htmlparser.tags.MetaTag; // meta
import org.htmlparser.tags.TitleTag; // meta
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.beans.LinkBean;
//import org.htmlparser.http.ConnectionManager; // meta
import java.util.*;
import java.net.URL;
import java.net.URLConnection; // meta
import java.io.*;
import java.lang.Math;

public class Crawler
{
	//URL queue
	private Vector<String> url;
	private Vector<String> history;

	// Crawl for num_pages pages, default is 30
	private static final int MAX_CRAWLED_PAGES = 1000;
	// Set the crawling target domain
	private static final String TARGET_CRAWLED_DOMAIN = "https://course.cse.ust.hk/comp4321/labs/TestPages/testpage.htm";

	//Imported packages for pipeline process
	private Database database;
	private StopStem stopStem;

	Crawler() throws Exception
	{
		url = new Vector<String>();
		url.add(TARGET_CRAWLED_DOMAIN);
		history = new Vector<String>();

		database = new Database();
		stopStem = new StopStem();
	}

	public double idf(int word_id, boolean isTitle) throws Exception
	{
		int N = database.urlMapTable.getMaxId();
		int df = 0;
		if(isTitle)
			df = database.titleInvertedIndex.getAllEntriesId(word_id).size();
		else
			df = database.invertedIndex.getAllEntriesId(word_id).size();
		return (Math.log(N) - Math.log(df)) / Math.log(2.0);
	}

	public boolean isEmpty()
	{
		return url.size() == 0;
	}

	public String getURL()
	{
		return url.remove(0);
	}

	public void setHistory(String link)
	{
		history.add(link);
	}

	public boolean crawled(String link)
	{
		return history.indexOf(link) != -1;
	}

	public String spaceToUnderscore(String text)
	{
		return text.replaceAll("\\s+","_");
	}

	// Get the last-modified-date without connection provided
	public String getLastModifiedDate(String target_url) throws Exception
	{
		// Create URL connection to retrieve header information
		URL url = new URL(target_url);
		URLConnection connection = url.openConnection();

		// Get last modification date
		String last_modified_date = connection.getHeaderField("Last-Modified");
		if (last_modified_date == "0" || last_modified_date == null)
			last_modified_date = "N/A";

		return String.valueOf(last_modified_date);
	}

	// Get the last-modified-date with connection provided
	public String getLastModifiedDate(String target_url, URLConnection connection) throws Exception
	{
		// Get last modification date
		String last_modified_date = connection.getHeaderField("Last-Modified");
		if (last_modified_date == "0" || last_modified_date == null)
			last_modified_date = "N/A";

		return String.valueOf(last_modified_date);
	}

	public void Finalize() throws Exception
	{
		//Stores weights(tf * idf) vector for each document
		int max_doc = database.urlMapTable.getMaxId();
		for(int i = 0; i < max_doc; i++)
		{
			// Handle document content
			database.vsmIndex.removeRow(i);
			//For each document, append all tf*idf to index
			Vector<Pair> doc = database.forwardIndex.getAllEntriesId(i);
			if(doc == null)
				continue;

			int max_tf = 0;
			for(int j = 0; j < doc.size(); j++)
				if(doc.get(j).Value > max_tf)
					max_tf = doc.get(j).Value;

			for(int j = 0; j < doc.size(); j++)
			{
				Pair word = doc.get(j);
				//length of entries per word = df of the word
				database.vsmIndex.appendEntry(i, word.Key, word.Value * idf(word.Key, false) / max_tf);
			}


			// Handle document title
			database.titleVsmIndex.removeRow(i);
			//For each document, append all tf*idf to index
			Vector<Pair> doc_title = database.titleForwardIndex.getAllEntriesId(i);
			if(doc_title == null)
				continue;

			int max_tf_title = 0;
			for(int j = 0; j < doc_title.size(); j++)
				if(doc_title.get(j).Value > max_tf_title)
					max_tf_title = doc_title.get(j).Value;

			for(int j = 0; j < doc_title.size(); j++)
			{
				Pair word = doc_title.get(j);
				//length of entries per word = df of the word
				database.titleVsmIndex.appendEntry(i, word.Key, word.Value * idf(word.Key, true) / max_tf_title);
			}

		}

		database.Finalize();
	}

	//Extract words from the first url in the vector
	public Vector<String> extractWords(String parent) throws Exception
	{
		Vector<String> words = new Vector<String>();
		StringBean bean = new StringBean();

		bean.setURL(parent);
		bean.setLinks(false);
		String contents = bean.getStrings();
		StringTokenizer st = new StringTokenizer(contents);

		while (st.hasMoreTokens())
			words.add(st.nextToken());

		//All the text processing
		//Stop word removal, stemming, invalid word removal, to lower case
		Vector<String> stemmed = new Vector<String>();
		for(String word : words)
		{
			String p_word = word.replaceAll("[^\\w\\s]|_", "").trim().toLowerCase();
			if(!p_word.isEmpty() && !stopStem.isStopWord(p_word))
				stemmed.add(stopStem.stem(p_word));
		}

		return stemmed;
	}

	// Extract metadata from a URL
	public Vector<String> extractMetas(String parent) throws Exception
	{
		Parser parser = new Parser();
		TagNameFilter filter = new TagNameFilter("title");
		Vector<String> metas = new Vector<String>();

		try {
            parser.setResource(parent);
            NodeList list = parser.parse(filter);
            Node node = list.elementAt(0);

            if (node instanceof TitleTag) {
            	TitleTag titleTag = (TitleTag) node;
            	String title = titleTag.getTitle();
            	metas.add(title);
            }
        } catch (ParserException e) {
            e.printStackTrace();
        }

        // Create URL connection to retrieve header information
		URL url = new URL(parent);
		URLConnection connection = url.openConnection();

		// Get last-modified-date
		metas.add(getLastModifiedDate(parent, connection));

		// Get document size
		String document_size = connection.getHeaderField("content-Length");
		metas.add(String.valueOf(document_size));

		// Show meta for each URL
		System.out.println(metas);

        return metas;
	}

	//Find the occurence of each word in the string vector, save the frequency to the database
	public void updateWordIndex(String url, Vector<String> words) throws Exception
	{
		// Get the Document ID
		int doc_id = database.urlMapTable.getKey(url);
		if(doc_id == -1)
			throw new Exception("Link not found, cannot insert word to index");

		// Collect all the words in a Hash Set
		HashSet<String> unique = new HashSet<String>(words);
		// Iterate through all the words in the document
		for(String word: unique)
		{
			// Get the term frequency(tf) of the word
			int freq = Collections.frequency(words, word);

			// Insert the word into wordMapTable and get the word ID
			int word_id = database.wordMapTable.appendEntry(word);

			// Insert the word into Inverted File: [word, document ID, term frequency]
			database.invertedIndex.updateEntry(word_id, doc_id, freq);

			// Insert the word into the Forward Index: [document ID, word ID, term frequency]
			database.forwardIndex.updateEntry(doc_id, word_id, freq);
		}

		//Update word positions of a document
		database.positionIndex.removeRow(doc_id);
		for(int position = 0; position < words.size(); position++)
			database.positionIndex.appendEntry(doc_id, position, database.wordMapTable.getKey(words.get(position)));

	}

	//Find the occurence of each title in the string vector, save the frequency to the database
	public void updateTitleIndex(String url, Vector<String> words) throws Exception
	{
		// Get the Document ID
		int doc_id = database.urlMapTable.getKey(url);
		if(doc_id == -1)
			throw new Exception("Link not found, cannot insert word to index");

		// Collect all the words in a Hash Set
		HashSet<String> unique = new HashSet<String>(words);
		// Iterate through all the words in the document
		for(String word: unique)
		{
			// Get the term frequency(tf) of the word
			int freq = Collections.frequency(words, word);
			// Insert the word into wordMapTable and get the word ID
			int word_id = database.wordMapTable.appendEntry(word);
			// Insert the word into Inverted File: [word, document ID, term frequency]
			database.titleInvertedIndex.updateEntry(word_id, doc_id, freq);
			// Insert the word into the Forward Index: [document ID, word ID, term frequency]
			database.titleForwardIndex.updateEntry(doc_id, word_id, freq);
		}
	}

	public void updateLinkIndex(String url, Vector<String> links) throws Exception
	{
		// Insert the url into urlMapTable and get the url ID
		int url_id = database.urlMapTable.appendEntry(url);

		// Remove the out-dated url's child links data
		database.linkIndex.removeRow(url_id);

		// Iterate through all the child links of the url
		int index = 0;
		for(String link: links)
		{
			int link_id = database.urlMapTable.appendEntry(link);
			// Insert the child links into the Link Index: [url ID, link index, child link id]
			database.linkIndex.appendEntry(url_id, index++, link_id);
		}
	}

	public void updateParentIndex(String url, Vector<String> links) throws Exception{
		// Get the url ID
		int url_id = database.urlMapTable.getKey(url);
		if(url_id == -1)
			throw new Exception("Link not found, cannot insert word to index");

		// Iterate through all the child links of the url
		int index = 0;
		for(String link: links)
		{
			System.out.println("parent URL:"+url+", corresponding ID:"+url_id);
			int link_id = database.urlMapTable.getKey(link);
			System.out.println("child URL:"+link+", corresponding ID:"+link_id);
			// Insert the parent links into the Link Index: [link ID, link index, parent link id]
			database.parentIndex.appendEntry(link_id, index++, url_id);
			System.out.println("append:"+link_id+", "+url_id);
		}
	}

	public void removeAllParent() throws Exception{
		database.parentIndex.removeAll();
	}

	public void clearAll() throws Exception
	{
		database.ClearAll();
	}

	public void updateMetaIndex(String url, Vector<String> metas) throws Exception
	{
		// Get the Document ID
		int url_id = database.urlMapTable.getKey(url);
		if(url_id == -1)
			throw new Exception("Link not found, cannot insert word to index");

		// Remove the out-dated url's meta data
		database.metaIndex.removeRow(url_id);

		// Update Meta data
		int index = 0;
		for(String meta: metas)
		{
			String underscored_meta = spaceToUnderscore(meta);
			// Insert the metadata into the Link Index: [url ID, index, meta]
			// meta = ["title", "last modified date", "size of page"]
			database.metaIndex.appendEntry(url_id, index++, underscored_meta);
		}

		String title = metas.get(0);
		String[] title_array = title.split(" ");

		Vector<String> stemmedTitle = new Vector<String>();
		for(String word : title_array)
		{
			String p_word = word.replaceAll("[^\\w\\s]|_", "").trim().toLowerCase();
			if(!p_word.isEmpty() && !stopStem.isStopWord(p_word))
				stemmedTitle.add(stopStem.stem(p_word));
		}

		//System.out.println(stemmedTitle.toString());
		updateTitleIndex(url, stemmedTitle);
	}

	//Extract all links in the website
	public Vector<String> extractLinks(String parent) throws Exception
	{
		Vector<String> link = new Vector<String>();
		LinkBean bean = new LinkBean();

		bean.setURL(parent);
		URL[] urls = bean.getLinks();

		for (URL s : urls)
		{
			String str = s.toString().split("#")[0].split("\\?")[0].replaceAll("/$", "");
			link.add(str);
		}

		url.addAll(link);

		return link;
	}

	public int crawl() throws Exception
	{
		if(url.isEmpty())
			return -1;

		// Pops first element, i.e. BFS
		String link = getURL();
		if(crawled(link))
			return 0;

		// Check the last-modified-date. 
		// If inserted but updated externally, need to crawl again, otherwise return.
		int stored_key = database.urlMapTable.getKey(link);
		// The link is inserted already
		if(stored_key != -1){
			String last_modified_date = getLastModifiedDate(link);
			//System.out.println("Testing: last_modified_date: " + last_modified_date);
			//System.out.println(stored_key);

			Vector<String> stored_meta = database.metaIndex.getAllEntriesMeta(stored_key);

			//System.out.println(stored_meta);
			
            if (stored_meta != null){
				String stored_last_modified_date = stored_meta.get(1);
				System.out.println("======================== stored date!!" + stored_last_modified_date);

				// If True, then there's no update on the page
				if(stored_last_modified_date.equals(last_modified_date)){
					System.out.println("Skip fresh link crawled");
					extractLinks(link);
					setHistory(link);
					return 0;
				}
			}
		}


		System.out.println(link);

		Vector<String> extractedLinks = extractLinks(link);
		// Extract links: create Link Index and parentIndex
		updateLinkIndex(link, extractedLinks);
		updateParentIndex(link, extractedLinks);
		// Extract words: create Inverted Index & Forward Index
		updateWordIndex(link, extractWords(link));
		// Update meta data for each page
		updateMetaIndex(link, extractMetas(link));

		setHistory(link);

		return 1;
	}

	public static void main (String[] args)
	{
		try
		{
			Crawler crawler = new Crawler();
			// clear all data in parentIndex
			//crawler.removeAllParent();
			// Initialization
			System.out.println("Initializing..");

			System.out.print("Base URL: ");
			for(int i = 1; i <= MAX_CRAWLED_PAGES;)
			{
				int state = crawler.crawl();
				if(state > 0)
					System.out.print("Website " + (i++) + ": ");
				else if(state < 0)
					break;
			}
			System.out.println("Max Reached");

			// Save the database
			crawler.Finalize();

			// rank table generates
			Rank rank = new Rank();
			rank.initializeAll();
			rank.calculateAll();
			rank.printAll();
			rank.Finalize();
		}
		catch (Exception e)
		{
			System.err.println("Error");
			System.err.println(e.toString());
		}
	}
}
