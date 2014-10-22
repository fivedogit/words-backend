
package co.ords.w;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_hostnamelikes2") 
public class HostnameLikeItem implements java.lang.Comparable<HostnameLikeItem> {

	private String hostname;
	private long msfe;
	private String timestamp_hr;
	private String author_id;
	private String id;
	
	@DynamoDBHashKey(attributeName="id")  
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexNames={"author_id-hostname-index","author_id-msfe-index"}, attributeName="author_id") 
	public String getAuthorId() {return author_id; }
	public void setAuthorId(String author_id) { this.author_id = author_id; }
	
	@DynamoDBIndexRangeKey(attributeName="hostname", globalSecondaryIndexName="author_id-hostname-index") 
	@DynamoDBIndexHashKey( globalSecondaryIndexName="hostname-msfe-index", attributeName="hostname")   
	public String getHostname() {return hostname; }
	public void setHostname(String hostname) { this.hostname = hostname; }
	
	@DynamoDBAttribute(attributeName="msfe")  
	@DynamoDBIndexRangeKey(attributeName="msfe", globalSecondaryIndexNames={"hostname-msfe-index","author_id-msfe-index"}) 
	public long getMSFE() {return msfe; }
	public void setMSFE(long msfe) { this.msfe = msfe; }
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBIgnore
	public int compareTo(HostnameLikeItem o) // this makes more recent objects come first
	{
	    Long othertimestamp = ((HostnameLikeItem)o).getMSFE();
	    int x = othertimestamp.compareTo(getMSFE());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
}
