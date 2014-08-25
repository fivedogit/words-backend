
package co.ords.w;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;


@DynamoDBTable(tableName="words_hostnames")
public class HostnameItem implements java.lang.Comparable<HostnameItem> {

	// static parts of the database entry
	private String hostname;
	private boolean separated;
	private String original_url; // always standardized
	
	// dynamic parts (not in the database entry itself)
	private TreeSet<HPItem> hps;
	private TreeSet<HPQSPItem> hpqsps;
	private TreeSet<CommentItem> comments;
	private TreeSet<HostnameLikeItem> hostname_likes;
	private TreeSet<HPQSPLikeItem> hpqsp_likes;
	
	@DynamoDBHashKey(attributeName="hostname")  
	public String getHostname() {return hostname; }
	public void setHostname(String hostname) { this.hostname = hostname; }
	
	@DynamoDBAttribute(attributeName="separated")  
	public boolean getSeparated() {return separated; }
	public void setSeparated(boolean separated) { this.separated = separated; }
	
	@DynamoDBAttribute(attributeName="original_url")  
	public String getOriginalURL() {return original_url; }
	public void setOriginalURL(String original_url) { this.original_url = original_url; }
	
	@DynamoDBIgnore
	public TreeSet<HPItem> getHPs(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) {  // gets all HPQSPs attached to this Hostname
		if(hps != null) // already populated
			return hps;
		else
		{	
			// set up an expression to query hostname + hp (all)
	        DynamoDBQueryExpression<HPItem> queryExpression = new DynamoDBQueryExpression<HPItem>()
	        		.withIndexName("hostname-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the hostname part
	        HPItem hpkey = new HPItem();
	        hpkey.setHostname(getHostname());
	        queryExpression.setHashKeyValues(hpkey);
	        
			// execute
	        List<HPItem> hpitems = mapper.query(HPItem.class, queryExpression, dynamo_config);
	        if(hpitems != null && hpitems.size() > 0)
	        {
	        	hps = new TreeSet<HPItem>();
	        }
	        for (HPItem hpitem : hpitems) {
	            //System.out.format("Parent=%s, Id=%s",
	            //       dislikeitem.getParent(), dislikeitem.getId());
	        	hps.add(hpitem);
	        }	
			return hps;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<HPQSPItem> getHPQSPs(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) {  // gets all HPQSPs attached to this Hostname
		if(hpqsps != null) // already populated
			return hpqsps;
		else
		{	
			// set up an expression to query hostname + hpqsp (all)
	        DynamoDBQueryExpression<HPQSPItem> queryExpression = new DynamoDBQueryExpression<HPQSPItem>()
	        		.withIndexName("hostname-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the hostname part
	        HPQSPItem hpqsp_key = new HPQSPItem();
	        hpqsp_key.setHostname(getHostname());
	        queryExpression.setHashKeyValues(hpqsp_key);

			// execute
	        List<HPQSPItem> hpqspitems = mapper.query(HPQSPItem.class, queryExpression, dynamo_config);
	        if(hpqspitems != null && hpqspitems.size() > 0)
	        {
	        	//System.out.println("The hostname:hpqsps " + getHostname() + ":* query returned " + hpqspitems.size() + " results"); 
	        	hpqsps = new TreeSet<HPQSPItem>();
	        }
	        for (HPQSPItem hpqspitem : hpqspitems) {
	        	hpqsps.add(hpqspitem);
	        }
			return hpqsps;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<CommentItem> getAllComments(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) {  // gets all Comments attached to this Hostname, used for comment retrieval on combined hostnames
		if(comments != null) // already populated
			return comments;
		else
		{	
			// set up an expression to query hostname + msfe (all)
	        DynamoDBQueryExpression<CommentItem> queryExpression = new DynamoDBQueryExpression<CommentItem>()
	        		.withIndexName("hostname-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the hostname part
	        CommentItem comment_key = new CommentItem();
	        comment_key.setHostname(getHostname());
	        queryExpression.setHashKeyValues(comment_key);

	        // set the msfe range part
	        if(minutes_ago > 0)
	        {
	        	//System.out.println("Getting comments under this hostname with a valid cutoff time.");
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
	        {
	        	comments = new TreeSet<CommentItem>();
	        }
	        for (CommentItem commentitem : commentitems) {
	        	comments.add(commentitem);
	        }
		        
			return comments;
		}
	}
	
	  public long getNumberOfHostnameLikes(String hostname_str, int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	  {
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<HostnameLikeItem> queryExpression = new DynamoDBQueryExpression<HostnameLikeItem>()
	        		.withIndexName("hostname-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the parent part
	        HostnameLikeItem hostnameKey = new HostnameLikeItem();
	        hostnameKey.setHostname(hostname_str);
	        queryExpression.setHashKeyValues(hostnameKey);
	        
	        // set the msfe range part
	        if(minutes_ago > 0)
	        {
	        	//System.out.println("Getting hostname likes with a valid cutoff time.");
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
	        List<HostnameLikeItem> items = mapper.query(HostnameLikeItem.class, queryExpression, dynamo_config);
	        if(items != null && items.size() > 0)
	        	return items.size();
	        else
	        	return 0;
			
	  }
	
	  @DynamoDBIgnore
	  public TreeSet<HostnameLikeItem> getHostnameLikes(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) {  // gets all HostnameLikeItems attached to this Hostname, used for comment retrieval on combined hostnames
			if(hostname_likes != null) // already populated
				return hostname_likes;
			else
			{	
				// set up an expression to query hostname + msfe (all)
		        DynamoDBQueryExpression<HostnameLikeItem> queryExpression = new DynamoDBQueryExpression<HostnameLikeItem>()
		        		.withIndexName("hostname-msfe-index")
						.withScanIndexForward(true)
						.withConsistentRead(false);
		        
		        // set the hostname part
		        HostnameLikeItem hostnamelike_key = new HostnameLikeItem();
		        hostnamelike_key.setHostname(getHostname());
		        queryExpression.setHashKeyValues(hostnamelike_key);

		        // set the msfe range part
		        if(minutes_ago > 0)
		        {
		        	//System.out.println("Getting hostnamelikes with a valid cutoff time.");
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
		        List<HostnameLikeItem> items = mapper.query(HostnameLikeItem.class, queryExpression, dynamo_config);
		        if(items != null && items.size() > 0)
		        {
		        	hostname_likes = new TreeSet<HostnameLikeItem>();
		        }
		        for (HostnameLikeItem item : items) {
		        	hostname_likes.add(item);
		        }
					
				return hostname_likes;
			}
		}  
	  
	@DynamoDBIgnore
	public TreeSet<HPQSPLikeItem> getHPQSPLikes(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) {  // gets all HPQSPLikeItems attached to this Hostname, used for comment retrieval on combined hostnames
		if(hpqsp_likes != null) // already populated
			return hpqsp_likes;
		else
		{	
			// set up an expression to query hostname + msfe (all)
	        DynamoDBQueryExpression<HPQSPLikeItem> queryExpression = new DynamoDBQueryExpression<HPQSPLikeItem>()
	        		.withIndexName("hostname-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the hostname part
	        HPQSPLikeItem hpqsplike_key = new HPQSPLikeItem();
	        hpqsplike_key.setHostname(getHostname());
	        queryExpression.setHashKeyValues(hpqsplike_key);

	        // set the msfe range part
	        if(minutes_ago > 0)
	        {
	        	//System.out.println("Getting hpqsplikes with a valid cutoff time.");
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
	        {
	        	hpqsp_likes = new TreeSet<HPQSPLikeItem>();
	        }
	        for (HPQSPLikeItem item : items) {
	        	hpqsp_likes.add(item);
	        }
			return hpqsp_likes;
		}
	}
	
	@DynamoDBIgnore
	public int compareTo(HostnameItem o) // this makes more recent comments come first
	{
	    String otherhostname = ((HostnameItem)o).getHostname();
	    int x = otherhostname.compareTo(getHostname());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
}
