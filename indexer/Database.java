import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.htree.HTree;
import java.io.IOException;

public class Database
{
	// Database filename
	private static final String DATABASE_NAME = "indexDB";
	// Hashtable filenames
	private static final String[] HASHTABLE_NAME = {"inverted", "forward", "link", "vsm", "position", "word", "url", "meta", "parent", "title", "titlevsm", "titleforward"};

	private RecordManager recman;

	//For direct access of functions
	public Index 	invertedIndex;
	public Index	forwardIndex;
	public Index	linkIndex;
	public Index	vsmIndex;
	public Index	positionIndex;
	public Index    metaIndex;
	public Index	parentIndex;
	public Index    titleInvertedIndex;
	public Index    titleVsmIndex;
	public Index    titleForwardIndex;

	public MapTable	wordMapTable;
	public MapTable	urlMapTable;

	public HTree rank;

	Database() throws IOException
	{
		recman = RecordManagerFactory.createRecordManager(DATABASE_NAME);

		invertedIndex = new Index(LoadOrCreate(HASHTABLE_NAME[0]), "D");
		forwardIndex  = new Index(LoadOrCreate(HASHTABLE_NAME[1]), "W");
		linkIndex	  = new Index(LoadOrCreate(HASHTABLE_NAME[2]), "L");
		vsmIndex      = new Index(LoadOrCreate(HASHTABLE_NAME[3]), "W");
		positionIndex = new Index(LoadOrCreate(HASHTABLE_NAME[4]), "P");
		metaIndex	  = new Index(LoadOrCreate(HASHTABLE_NAME[7]), "M");
		parentIndex   = new Index(LoadOrCreate(HASHTABLE_NAME[8]), "A");
		titleInvertedIndex = new Index(LoadOrCreate(HASHTABLE_NAME[9]), "T");
		titleVsmIndex = new Index(LoadOrCreate(HASHTABLE_NAME[10]), "U");
		titleForwardIndex = new Index(LoadOrCreate(HASHTABLE_NAME[11]), "F");

		wordMapTable  = new MapTable(LoadOrCreate(HASHTABLE_NAME[5]), LoadOrCreate("inverted_" + HASHTABLE_NAME[5]));
		urlMapTable	  = new MapTable(LoadOrCreate(HASHTABLE_NAME[6]), LoadOrCreate("inverted_" + HASHTABLE_NAME[6]));

	}

	// Load the database given the target table name, or create a new one when first try
	private HTree LoadOrCreate(String hashtable_name) throws IOException
	{
		// Load the target database
		long recid = recman.getNamedObject(hashtable_name);
		if(recid != 0)
		{
			// Return Hash Table if found
			System.out.println("Hashtable found, id: " + recid);
			return HTree.load(recman, recid);
		}
		else
		{
			// Create a new Hash Table if not found
			HTree hashtable = HTree.createInstance(recman);
			recid = hashtable.getRecid();
			recman.setNamedObject(hashtable_name, recid);
			System.out.println("Hashtable not found, new id: " + recid);
			return hashtable;
		}
	}

	public void ClearAll() throws IOException
	{
		invertedIndex.removeAll();
		forwardIndex.removeAll();
		linkIndex.removeAll();
		vsmIndex.removeAll();
		positionIndex.removeAll();
		metaIndex.removeAll();
		parentIndex.removeAll();
		titleInvertedIndex.removeAll();
		titleVsmIndex.removeAll();
		titleForwardIndex.removeAll();

		wordMapTable.removeAll();
		urlMapTable.removeAll();
	}

	// Save and confirm the changes of the database
	public void Finalize() throws IOException
	{
		recman.commit();
		recman.close();
		System.out.println("Closed");
	}

	public void Save() throws IOException
	{
		recman.commit();
		System.out.println("Saved");
	}

	public static void main(String[] args)
	{
		try
		{
			Database db = new Database();

			// Read in the prompt input from user
			String hashtable_name = args[0];
			// Reverse the display order when the second input is "backward"
			boolean order = args.length > 1? !args[1].equals("backward"): true;

			// Print all the data in the Hash Table
			if(hashtable_name.equals("inverted"))
			{
				System.out.println("Inverted");
				db.invertedIndex.printAll();
			}
			else if(hashtable_name.equals("forward"))
			{
				System.out.println("Forward");
				db.forwardIndex.printAll();
			}
			else if(hashtable_name.equals("link"))
			{
				System.out.println("Links");
				db.linkIndex.printAll();
			}
			else if(hashtable_name.equals("vsm"))
			{
				System.out.println("VSMs");
				db.vsmIndex.printAll();
			}
			else if(hashtable_name.equals("position"))
			{
				System.out.println("Positions");
				db.positionIndex.printAll();
			}
			else if(hashtable_name.equals("meta"))
			{
				System.out.println("Metas");
				db.metaIndex.printAll();
			}
			else if(hashtable_name.equals("word"))
			{
				System.out.println("Word");
				db.wordMapTable.printAll(order);
			}
			else if(hashtable_name.equals("parent")){
				System.out.println("Parents");
				db.parentIndex.printAll();
			}
			else if(hashtable_name.equals("url"))
			{
				System.out.println("Urls");
				db.urlMapTable.printAll(order);
			}
			else if(hashtable_name.equals("title"))
			{
				System.out.println("Title Inverted");
				db.titleInvertedIndex.printAll();
			}
			else if(hashtable_name.equals("titlevsm"))
			{
				System.out.println("Title VSMs");
				db.titleVsmIndex.printAll();
			}
			else if(hashtable_name.equals("titleforward"))
			{
				System.out.println("Title Forward");
				db.titleForwardIndex.printAll();
			}
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}

	}
}
