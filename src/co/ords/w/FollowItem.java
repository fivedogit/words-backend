
package co.ords.w;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_follows")
public class FollowItem {

	private String significant_designation;
	private String user_id;
	
	@DynamoDBHashKey(attributeName="significant_designation")  
	public String getSignificantDesignation() {return significant_designation; }
	public void setSignificantDesignation(String significant_designation) { this.significant_designation = significant_designation; }
	
	@DynamoDBRangeKey(attributeName="user_id")  
	public String getUserId() {return user_id; }
	public void setUserId(String user_id) { this.user_id = user_id; }
	
}
