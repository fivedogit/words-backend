package co.ords.w;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_threadviews")
public class ThreadViewItem implements java.lang.Comparable<ThreadViewItem> {

	/**
	 * This class stores each threadview for metric tracking purposes: 
	 * Was user logged in? Was the thread disappointingly empty? Was it a separated hostname or hp with sqsps?
	 * It DOES NOT save any identifiable information such as username or IP address.
	 * There is zero information that could be tied to an individual user.
	 */
	
	private String id;
	private long msfe;
	private String timestamp_hr;
	private boolean was_empty;
	private boolean was_logged_in;
	private boolean hostname_was_separated;
	private boolean hp_had_sqsps;
	
	// in order to make, you know, money from this -- without which WORDS is unsustainable -- We need to know how many views
	// a thread has gotten over a given period of time to gauge its value.
	private String significant_designation; // thus, we have to record the significant_designation of this view
	
	@DynamoDBHashKey(attributeName="id") 
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBAttribute(attributeName="msfe")  
	@DynamoDBIndexRangeKey(attributeName="msfe", globalSecondaryIndexName="significant_designation-msfe-index")
	public long getMSFE() {return msfe; }
	public void setMSFE(long msfe) { this.msfe = msfe; }
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBAttribute(attributeName="was_empty")
	public boolean getWasEmpty() {return was_empty; }
	public void setWasEmpty(boolean was_empty) { this.was_empty = was_empty; }
	
	@DynamoDBAttribute(attributeName="was_logged_in")
	public boolean getWasLoggedIn() {return was_logged_in; }
	public void setWasLoggedIn(boolean was_logged_in) { this.was_logged_in = was_logged_in; }
	
	@DynamoDBAttribute(attributeName="hostname_was_separated")
	public boolean getHostnameWasSeparated() {return hostname_was_separated; }
	public void setHostnameWasSeparated(boolean hostname_was_separated) { this.hostname_was_separated = hostname_was_separated; }
	
	@DynamoDBAttribute(attributeName="hp_had_sqsps")
	public boolean getHPHadSQSPS() {return hp_had_sqsps; }
	public void setHPHadSQSPS(boolean hp_had_sqsps) { this.hp_had_sqsps = hp_had_sqsps; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="significant_designation-msfe-index", attributeName="significant_designation")   
	public String getSignificantDesignation() {return significant_designation; }
	public void setSignificantDesignation(String significant_designation) { this.significant_designation = significant_designation; }
	
	@DynamoDBIgnore
	public int compareTo(ThreadViewItem o) // this makes more recent comments come first
	{
		 Long othertimestamp = ((ThreadViewItem)o).getMSFE();
		 int x = othertimestamp.compareTo(getMSFE());
		 if(x >= 0) // this is to prevent equals
			 return 1;
		 else
			 return -1;
	}
}