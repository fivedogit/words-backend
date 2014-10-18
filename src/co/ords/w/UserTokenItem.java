
package co.ords.w;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName="words_user_tokens") 
public class UserTokenItem {

	private String screenname;
	private String token;
	
	@DynamoDBIndexHashKey(attributeName="screenname") // screenname is the hash key for screenname-email
	public String getScreenname() {return screenname; }
	public void setScreenname(String screenname) { this.screenname = screenname; }

	@DynamoDBAttribute(attributeName="token")  
	public String getToken() {return token; }
	public void setToken(String token) { this.token = token; }
	
}
