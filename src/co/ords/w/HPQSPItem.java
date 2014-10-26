
package co.ords.w;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@DynamoDBTable(tableName="words_hpqsps")
public class HPQSPItem implements java.lang.Comparable<HPQSPItem> {

	// static parts of the database entry
	private String hpqsp;
	private String hp;
	private String hostname;
	private String original_url; // always standardized
	
	// dynamic parts (not in the database entry itself)
	private TreeSet<CommentItem> allcomments;
	private TreeSet<CommentItem> toplevelcomments;
	private TreeSet<HPQSPLikeItem> hpqsp_likes;
	
	@DynamoDBHashKey(attributeName="hpqsp")  
	public String getHPQSP() {return hpqsp; }
	public void setHPQSP(String hpqsp) { this.hpqsp = hpqsp; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="hp-index", attributeName="hp")
	public String getHP() {return hp; }
	public void setHP(String hp) { this.hp = hp; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="hostname-index", attributeName="hostname")
	public String getHostname() {return hostname; }
	public void setHostname(String hostname) { this.hostname = hostname; }
	
	@DynamoDBAttribute(attributeName="original_url")  
	public String getOriginalURL() {return original_url; }
	public void setOriginalURL(String original_url) { this.original_url = original_url; }
	
	// This function gets all comments whose "hpqsp" value matches this hpqsp string, including comments of any depth 
	@DynamoDBIgnore
	public TreeSet<CommentItem> getAllComments(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(allcomments != null) // already populated
			return allcomments;
		else
		{	
				// set up an expression to query screename#id
		        DynamoDBQueryExpression<CommentItem> queryExpression = new DynamoDBQueryExpression<CommentItem>()
		        		.withIndexName("hpqsp-msfe-index")
						.withScanIndexForward(true)
						.withConsistentRead(false);
		        
		        // set the parent part
		        CommentItem commentKey = new CommentItem();
		        commentKey.setHPQSP(getHPQSP());
		        queryExpression.setHashKeyValues(commentKey);
		        
		        // set the msfe range part
		        if(minutes_ago > 0)
		        {
		        	//System.out.println("Getting comment children with a valid cutoff time.");
		        	Calendar cal = Calendar.getInstance();
		        	cal.add(Calendar.MINUTE, (minutes_ago * -1));
		        	long msfe_cutoff = cal.getTimeInMillis();
			        // set the msfe range part
			        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
			    	keyConditions.put("msfe",new Condition()
			    	.withComparisonOperator(ComparisonOperator.GT)
					.withAttributeValueList(new AttributeValue().withN(new Long(msfe_cutoff).toString())));
					queryExpression.setRangeKeyConditions(keyConditions);
		        }

				// execute
		        List<CommentItem> commentitems = mapper.query(CommentItem.class, queryExpression, dynamo_config);
		        if(commentitems != null && commentitems.size() > 0)
		        	allcomments = new TreeSet<CommentItem>();
		        for (CommentItem commentitem : commentitems) {
		            //System.out.format("Parent=%s, Id=%s",
		            //       dislikeitem.getParent(), dislikeitem.getId());
		        	allcomments.add(commentitem);
		        }
			return allcomments;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<CommentItem> getTopLevelComments(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(toplevelcomments != null) // already populated
			return toplevelcomments;
		else
		{	
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<CommentItem> queryExpression = new DynamoDBQueryExpression<CommentItem>()
	        		.withIndexName("parent-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the parent part
	        CommentItem commentKey = new CommentItem();
	        commentKey.setParent(getHPQSP());
	        queryExpression.setHashKeyValues(commentKey);
	        
	        // set the msfe range part
	        if(minutes_ago > 0)
	        {
	        	//System.out.println("Getting comment children with a valid cutoff time.1");
	        	Calendar cal = Calendar.getInstance();
	        	cal.add(Calendar.MINUTE, (minutes_ago * -1));
	        	long msfe_cutoff = cal.getTimeInMillis();
		        // set the msfe range part
		        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		    	keyConditions.put("msfe",new Condition()
		    	.withComparisonOperator(ComparisonOperator.GT)
				.withAttributeValueList(new AttributeValue().withN(new Long(msfe_cutoff).toString())));
				queryExpression.setRangeKeyConditions(keyConditions);
	        }
			// execute
	        List<CommentItem> commentitems = mapper.query(CommentItem.class, queryExpression, dynamo_config);
	        if(commentitems != null && commentitems.size() > 0)
	        	toplevelcomments = new TreeSet<CommentItem>();
	        for (CommentItem commentitem : commentitems) {
	            toplevelcomments.add(commentitem);
	        }
			return toplevelcomments;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<HPQSPLikeItem> getHPQSPLikes(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(hpqsp_likes != null) // already populated
			return hpqsp_likes;
		else
		{	
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<HPQSPLikeItem> queryExpression = new DynamoDBQueryExpression<HPQSPLikeItem>()
	        		.withIndexName("hpqsp-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the parent part
	        HPQSPLikeItem hpqsp_likeKey = new HPQSPLikeItem();
	        hpqsp_likeKey.setHPQSP(getHPQSP());
	        queryExpression.setHashKeyValues(hpqsp_likeKey);
	        
	        // set the msfe range part
	        if(minutes_ago > 0)
	        {
	        	//System.out.println("Getting hpqsp_like children with a valid cutoff time.");
	        	Calendar cal = Calendar.getInstance();
	        	cal.add(Calendar.MINUTE, (minutes_ago * -1));
	        	long msfe_cutoff = cal.getTimeInMillis();
		        // set the msfe range part
		        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		    	keyConditions.put("msfe",new Condition()
		    	.withComparisonOperator(ComparisonOperator.GT)
				.withAttributeValueList(new AttributeValue().withN(new Long(msfe_cutoff).toString())));
				queryExpression.setRangeKeyConditions(keyConditions);
	        }

			// execute
	        List<HPQSPLikeItem> hpqsp_likeitems = mapper.query(HPQSPLikeItem.class, queryExpression, dynamo_config);
	        if(hpqsp_likeitems != null && hpqsp_likeitems.size() > 0)
	        	hpqsp_likes = new TreeSet<HPQSPLikeItem>();
	        for (HPQSPLikeItem hpqsp_likeitem : hpqsp_likeitems) {
	            //System.out.format("Parent=%s, Id=%s",
	            //       dislikeitem.getParent(), dislikeitem.getId());
	        	hpqsp_likes.add(hpqsp_likeitem);
	        }
			return hpqsp_likes;
		}
	}

	@DynamoDBIgnore // this somewhat duplicates the above function, but is faster since we're not trying to get all the comments, just a count.
	public long getNumberOfHPQSPLikes(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) 
	{ 
		// set up an expression to query screename#id
        DynamoDBQueryExpression<HPQSPLikeItem> queryExpression = new DynamoDBQueryExpression<HPQSPLikeItem>()
        		.withIndexName("hpqsp-msfe-index")
				.withScanIndexForward(true)
				.withConsistentRead(false);
        
        // set the parent part
        HPQSPLikeItem hpqsp_likeKey = new HPQSPLikeItem();
        hpqsp_likeKey.setHPQSP(getHPQSP());
        queryExpression.setHashKeyValues(hpqsp_likeKey);
        
        // set the msfe range part
        if(minutes_ago > 0) // 0 means get ALL, regardless of time
        {
        	//System.out.println("Getting hpqsp_like children with a valid cutoff time.");
        	Calendar cal = Calendar.getInstance();
        	cal.add(Calendar.MINUTE, (minutes_ago * -1));
        	long msfe_cutoff = cal.getTimeInMillis();
	        // set the msfe range part
	        Map<String, Condition> keyConditions = new HashMap<String, Condition>();
	    	keyConditions.put("msfe",new Condition()
	    	.withComparisonOperator(ComparisonOperator.GT)
			.withAttributeValueList(new AttributeValue().withN(new Long(msfe_cutoff).toString())));
			queryExpression.setRangeKeyConditions(keyConditions);
        }

		// execute
        List<HPQSPLikeItem> items = mapper.query(HPQSPLikeItem.class, queryExpression, dynamo_config);
        if(items != null && items.size() > 0)
        	return items.size();
        else
        	return 0L;
	}
	
	@DynamoDBIgnore
	public int compareTo(HPQSPItem o) // this makes more recent comments come first
	{
	    String otherhpqsp = ((HPQSPItem)o).getHPQSP();
	    int x = otherhpqsp.compareTo(getHPQSP());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
	
	@DynamoDBIgnore
	public static void main(String [] args)
	{
	}
}
