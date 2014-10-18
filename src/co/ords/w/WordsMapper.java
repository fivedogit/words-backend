package co.ords.w;

import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;

public class WordsMapper extends DynamoDBMapper {
	
	public WordsMapper(AmazonDynamoDB dynamoDB) {
		super(dynamoDB);
		// TODO Auto-generated constructor stub
	}

	public UserItem getUserItemFromScreenname(String screenname)
	{
		// set up an expression to query screename#id
        DynamoDBQueryExpression<UserItem> queryExpression = new DynamoDBQueryExpression<UserItem>()
        		.withIndexName("screenname-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
        
        // set the parent part
        UserItem userkey = new UserItem();
        userkey.setScreenname(screenname);
        queryExpression.setHashKeyValues(userkey);

		// execute
        List<UserItem> useritems = this.query(UserItem.class, queryExpression);
        if(useritems != null && useritems.size() > 1)
        {
        	System.err.println("ERROR! More than one email address for this screenname. Yikes! Returning null.");
        	return null;
        }
        if(useritems != null && useritems.size() == 1)
        {
        	return useritems.get(0);
        }
        else
        {
        	//System.out.println("No user found for that screenname. Returning null. This is OK if we're just testing screenname availability.");
        	return null;
        }
	}
	 
	// emails in the system are either @words4chrome.com placeholders OR real, confirmed email addresses
	public UserItem getUserItemFromEmail(String email)
	{
		// set up an expression to query screename#id
        DynamoDBQueryExpression<UserItem> queryExpression = new DynamoDBQueryExpression<UserItem>()
        		.withIndexName("email-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
        
        // set the parent part
        UserItem userkey = new UserItem();
        userkey.setEmail(email.toLowerCase());
        queryExpression.setHashKeyValues(userkey);

		// execute
        List<UserItem> useritems = this.query(UserItem.class, queryExpression);
        if(useritems != null && useritems.size() > 1)
        {
        	System.err.println("ERROR! More than one screenname for this email address. Yikes! Returning null.");
        	return null;
        }
        if(useritems != null && useritems.size() == 1)
        {
        	return useritems.get(0);
        }
        else
        {
        	//System.out.println("No user found for that email address. Returning null. This is OK if we're just testing email availability.");
        	return null;
        }	
	}
	
}
