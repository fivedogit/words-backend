
package co.ords.w;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_impressions")
public class ImpressionItem {

	private String id;
	private long impression_msfe;
	private long conversion_msfe;
	private String timestamp_hr;
	private String target;
	private String source_category; // What was the broader category? // "email", "footer", "about"
	
	@DynamoDBHashKey(attributeName="id")  
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBAttribute(attributeName="impression_msfe")  
	public long getImpressionMSFE() {return impression_msfe; }
	public void setImpressionMSFE(long impression_msfe) { this.impression_msfe = impression_msfe; }
	
	@DynamoDBAttribute(attributeName="conversion_msfe")  
	public long getConversionMSFE() {return conversion_msfe; }
	public void setConversionMSFE(long conversion_msfe) { this.conversion_msfe = conversion_msfe; }
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBAttribute(attributeName="target")  
	public String getTarget() {return target; }
	public void setTarget(String target) { this.target = target; }

	@DynamoDBAttribute(attributeName="source_category")  
	public String getSourceCategory() {return source_category; }
	public void setSourceCategory(String source_category) { this.source_category = source_category; }
	
}
