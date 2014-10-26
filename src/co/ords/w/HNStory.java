package co.ords.w;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_hnstories")
public class HNStory {
	
	private String url;
	private String id; // leave this as a string, no need to restrict to numeric, especially since we don't control this value.
						// if they want to start including letters, let 'em
	
	@DynamoDBHashKey(attributeName="url") 
	public String getURL() {return url; }
	public void setURL(String url) { this.url = url; }
	
	@DynamoDBAttribute(attributeName="id")  
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
}
