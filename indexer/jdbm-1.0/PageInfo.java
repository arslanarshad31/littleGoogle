import java.util.Vector;

public class PageInfo
{
	public String Title;
	public String Url;
	public String LastModifiedDate;
	public Int SizeOfPage;
	public Vector<String> KeywordVector;
	public Vector<String> ParentLinkVector;
	public Vector<String> ChildLinkVector;

	PageInfo(String title, String url, String lastModifiedDate, Int sizeOfPage, Vector<String> keywordVector, 
		Vector<String> parentLinkVector, Vector<String> childLinkVector)
	{
		Title = title;
		Url = url;
		LastModifiedDate = lastModifiedDate;
		SizeOfPage = sizeOfPage;
		KeywordVector = keywordVector;
		ParentLinkVector = parentLinkVector;
		ChildLinkVector = childLinkVector;
	}

}
