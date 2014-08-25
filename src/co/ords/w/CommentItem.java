package co.ords.w;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

@DynamoDBTable(tableName="words_comments")
public class CommentItem implements java.lang.Comparable<CommentItem> {

	/**
	 * This class provides useful methods for making sense out of comments THAT ALREADY EXIST IN THE DB. 
	 * For now, entirely new comments are created by TTU. 
	 */
	
	// part of the item itself 
	private String id;
	
	private String parent; // hpqsp or comment id
	private String text;
	private long msfe; 
	private String timestamp_hr;
	private String author_id;
	private String url_when_created;
	private String page_title;
	private String ip_address;
	private boolean hidden;
	//private boolean dummy;
	private String hostname;
	private String hpqsp;
	private int depth;
	
	// not part of the item
	private TreeSet<CommentItem> children;
	private TreeSet<LikeItem> likes;
	private TreeSet<DislikeItem> dislikes;
	
	@DynamoDBHashKey(attributeName="id") 
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="parent-msfe-index", attributeName="parent") 
	public String getParent() {return parent; }
	public void setParent(String parent) { this.parent = parent; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="author_id-msfe-index", attributeName="author_id")   
	public String getAuthorId() {return author_id; }
	public void setAuthorId(String author_id) { this.author_id = author_id; }
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="hostname-msfe-index", attributeName="hostname")   
	public String getHostname() {return hostname; }
	public void setHostname(String hostname) { this.hostname = hostname; }
	
	@DynamoDBAttribute(attributeName="text")  
	public String getText() {return text; }
	public void setText(String text) { this.text = text; }
	
	@DynamoDBIgnore
	public int getTextLength() { return text.length(); 	}
	
	@DynamoDBAttribute(attributeName="url_when_created")  
	public String getURLWhenCreated() {return url_when_created; }
	public void setURLWhenCreated(String url_when_created) { this.url_when_created = url_when_created; }
	
	@DynamoDBAttribute(attributeName="ip_address")  
	public String getIPAddress() {return ip_address; }
	public void setIPAddress(String ip_address) { this.ip_address = ip_address; }
	
	@DynamoDBAttribute(attributeName="msfe")  
	@DynamoDBIndexRangeKey(attributeName="msfe", globalSecondaryIndexNames={"parent-msfe-index", "author_id-msfe-index", "hostname-msfe-index", "hpqsp-msfe-index"}) 
	public long getMSFE() {return msfe; }
	public void setMSFE(long msfe) { this.msfe = msfe; }
	
	@DynamoDBIgnore
	public Long getMSFE_Long() { return (new Long(msfe));}
	@DynamoDBIgnore
	public String getMSFE_String() { return ((new Long(msfe)).toString());}
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBAttribute(attributeName="hidden")  
	public boolean getHidden() {return hidden; }
	public void setHidden(boolean hidden) { this.hidden = hidden; }

	/*@DynamoDBAttribute(attributeName="dummy")  
	public boolean getDummy() {return dummy; }
	public void setDummy(boolean dummy) { this.dummy = dummy; }*/
	
	@DynamoDBIndexHashKey( globalSecondaryIndexName="hpqsp-msfe-index", attributeName="hpqsp")  
	public String getHPQSP() {return hpqsp; }
	public void setHPQSP(String hpqsp) { this.hpqsp = hpqsp; }
	
	@DynamoDBAttribute(attributeName="page_title")  
	public String getPageTitle() {return page_title; }
	public void setPageTitle(String page_title) { this.page_title = page_title; }
	
	@DynamoDBAttribute(attributeName="depth")  
	public int getDepth() {return depth; }
	public void setDepth(int depth) { this.depth = depth; }
	
	@DynamoDBIgnore
	public TreeSet<CommentItem> getChildren(int minutes_ago, WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(children != null) // already populated
			return children;
		else
		{	
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<CommentItem> queryExpression = new DynamoDBQueryExpression<CommentItem>()
	        		.withIndexName("parent-msfe-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the parent part
	        CommentItem commentKey = new CommentItem();
	        commentKey.setParent(getId());
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
	        	children = new TreeSet<CommentItem>();
	        for (CommentItem commentitem : commentitems) {
	            //System.out.format("Parent=%s, Id=%s",
	            //       dislikeitem.getParent(), dislikeitem.getId());
	        	children.add(commentitem);
	        }
			return children;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<LikeItem> getLikes(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(likes != null) // already populated
			return likes;
		else
		{	
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<LikeItem> queryExpression = new DynamoDBQueryExpression<LikeItem>()
	        		.withIndexName("parent-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the parent part
	        LikeItem likeKey = new LikeItem();
	        likeKey.setParent(getId());
	        queryExpression.setHashKeyValues(likeKey);

			// execute
	        List<LikeItem> likeitems = mapper.query(LikeItem.class, queryExpression, dynamo_config);
	        if(likeitems != null && likeitems.size() > 0)
	        	likes = new TreeSet<LikeItem>();
	        for (LikeItem likeitem : likeitems) {
	            //System.out.format("Parent=%s, Id=%s, URLWhenCreated=%s",
	            //       likeitem.getParent(), likeitem.getId(), likeitem.getURLWhenCreated(mapper, dynamo_config));
	        	likes.add(likeitem);
	        }
			return likes;
		}
	}
	
	@DynamoDBIgnore
	public TreeSet<DislikeItem> getDislikes(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { 
		if(dislikes != null) // already populated
			return dislikes;
		else
		{	
			// set up an expression to query screename#id
	        DynamoDBQueryExpression<DislikeItem> queryExpression = new DynamoDBQueryExpression<DislikeItem>()
	        		.withIndexName("parent-index")
					.withScanIndexForward(true)
					.withConsistentRead(false);
	        
	        // set the screenname part
	        DislikeItem dislikeKey = new DislikeItem();
	        dislikeKey.setParent(getId());
	        queryExpression.setHashKeyValues(dislikeKey);

			// execute
	        List<DislikeItem> dislikeitems = mapper.query(DislikeItem.class, queryExpression, dynamo_config);
	        if(dislikeitems != null && dislikeitems.size() > 0)
	        	dislikes = new TreeSet<DislikeItem>();
	        for (DislikeItem dislikeitem : dislikeitems) {
	            //System.out.format("Parent=%s, Id=%s, URLWhenCreated=%s",
	            //       likeitem.getParent(), likeitem.getId(), likeitem.getURLWhenCreated(mapper, dynamo_config));
	        	dislikes.add(dislikeitem);
	        }
			return dislikes;
		}
	}

	@DynamoDBIgnore
	public JSONObject getAsJSONObject(WordsMapper mapper, DynamoDBMapperConfig dynamo_config, boolean get_author_stuff_even_if_hidden)
	{
		JSONObject return_jo = new JSONObject();
		try {
			return_jo.put("id", this.getId());
			//System.out.println("COMMENTITEM ADDING PARENT=" + this.getParent());
			return_jo.put("parent", this.getParent());
			return_jo.put("msfe", this.getMSFE_String());
			Timestamp ts = new Timestamp(this.getMSFE());
			return_jo.put("time_ago", Global.agoIt(ts));
			return_jo.put("depth", this.getDepth());
			if(getURLWhenCreated().startsWith("https://"))
			{
				if(getHPQSP().endsWith("?"))
					return_jo.put("pseudo_url", "https://" + getHPQSP().substring(0,getHPQSP().length()-1));
				else
					return_jo.put("pseudo_url", "https://" + getHPQSP());
			}
			else
			{
				if(getHPQSP().endsWith("?"))
					return_jo.put("pseudo_url", "http://" + getHPQSP().substring(0,getHPQSP().length()-1));
				else
					return_jo.put("pseudo_url", "http://" + getHPQSP());
			}
			//return_jo.put("url_when_created", this.getURLWhenCreated());
			return_jo.put("hidden", this.getHidden());
			if(!getHidden() || get_author_stuff_even_if_hidden) // if not hidden, go ahead and include the comment text + minimal author information
			{
				return_jo.put("text", getText());
				UserItem useritem = mapper.load( UserItem.class, this.getAuthorId(), dynamo_config);
				return_jo.put("author_screenname", useritem.getScreennameLiteral());
				return_jo.put("author_picture", useritem.getPicture());
				return_jo.put("author_rating_in_window", useritem.getOrCalcRatingInWindow(mapper, dynamo_config, false)); // don't force calc of new rating
				return_jo.put("author_rating", useritem.getRating()); // don't force calc of new rating
			}
			JSONArray likes_ja = new JSONArray();
			TreeSet<LikeItem> likes = getLikes(mapper, dynamo_config);
			if(likes != null)
			{	
				Iterator<LikeItem> it = likes.iterator();
				LikeItem li = null;
				while(it.hasNext())
				{
					li = it.next();
					likes_ja.put(li.getId()); 
				}
			}
			return_jo.put("likes", likes_ja);
			JSONArray dislikes_ja = new JSONArray();
			TreeSet<DislikeItem> dislikes = getDislikes(mapper, dynamo_config);
			if(dislikes != null)
			{	
				Iterator<DislikeItem> it = dislikes.iterator();
				DislikeItem di = null;
				while(it.hasNext())
				{
					di = it.next();
					dislikes_ja.put(di.getId()); 
				}
			}
			return_jo.put("dislikes", dislikes_ja);
			JSONArray children_ja = new JSONArray();
			TreeSet<CommentItem> children = getChildren(0, mapper, dynamo_config);
			if(children != null)
			{	
				Iterator<CommentItem> it = children.iterator();
				CommentItem currentcomment = null;
				while(it.hasNext())
				{
					currentcomment = it.next();
					children_ja.put(currentcomment.getId()); // true/false doesn't matter here. No dislikes go past 2nd level.
				}
				
			}
			return_jo.put("children", children_ja);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return return_jo;
	}
	@DynamoDBIgnore
	public int getNumLikes(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { return getLikes(mapper, dynamo_config).size(); }
	
	@DynamoDBIgnore
	public int getNumDislikes(WordsMapper mapper, DynamoDBMapperConfig dynamo_config) { return getDislikes(mapper, dynamo_config).size(); }
	
	@DynamoDBIgnore
	public TreeSet<UserItem> getMentionedUsers(String inc_text, WordsMapper mapper)
	{
		String loc_text = null;
		if(inc_text == null)
			loc_text = text;
		else
			loc_text = inc_text;
		
		TreeSet<UserItem> mentioned_users = null;
		if(loc_text.indexOf("@") != -1) // there might be an @ mention here
		{
			// screenname must be 3-15 characters, all letters and numbers
			TreeSet<String> possiblematches = new TreeSet<String>();
			Pattern p_withtrailer = Pattern.compile("[ ][@][a-zA-Z]([a-zA-Z0-9]){2,14}[ ,!?\\.;:-]"); // match for mentions where trailed by an acceptable char
			Matcher matcher1 = p_withtrailer.matcher(loc_text); 
			while (matcher1.find()) {
			      possiblematches.add(matcher1.group().substring(2,matcher1.group().length() - 1));
			    }
			
			Pattern p_notrailer = Pattern.compile("[ ][@][a-zA-Z]([a-zA-Z0-9]){2,14}\\Z"); // match for mentions at end
			Matcher matcher2 = p_notrailer.matcher(loc_text); 
			while (matcher2.find()) {
			      possiblematches.add(matcher2.group().substring(2,matcher2.group().length()));
			    }
			
			Pattern p_beginning = Pattern.compile("\\A[@][a-zA-Z]([a-zA-Z0-9]){2,14}[ ,!?\\.;:-]"); // match for mentions where trailed by an acceptable char
			Matcher matcher3 = p_beginning.matcher(loc_text); 
			while (matcher3.find()) {
			      possiblematches.add(matcher3.group().substring(1,matcher3.group().length() - 1));
			    }
			
			if(possiblematches.size() > 0)
			{
				Iterator<String> it = possiblematches.iterator();
				String currentmatch = null;
				while(it.hasNext())
				{
					currentmatch = it.next();
					UserItem mentioned_user = mapper.getUserItemFromScreenname(currentmatch);
					if(mentioned_user != null)
					{
						if(mentioned_users == null)
							mentioned_users = new TreeSet<UserItem>();
						mentioned_users.add(mentioned_user);
					}
					else
					{
						//System.out.println(" ... not found. Dud.");
					}
				}
			}
			else
			{
				//System.out.print("Possible mentions == 0");
			}
		}
		return mentioned_users;
	}
	
	// NOTE there is no level 0. Starts at 1
	@DynamoDBIgnore
	public int getCommentDepth(WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		if(getParent().indexOf(".") != -1) // this comment's parent is not
		{
			//System.out.println("WC.getCommentDepth(): continuing level depth search. level=1");
			return 1;
		}
		else
		{
			CommentItem parent = mapper.load(CommentItem.class, getParent(), dynamo_config);
			int level = 1 + parent.getCommentDepth(mapper, dynamo_config);
			//System.out.println("WC.getCommentDepth(): continuing level depth search. level=" + level);
			return level;
		}
	}
	
	@DynamoDBIgnore
	public int compareTo(CommentItem o) // this makes more recent comments come first
	{
	    Long othertimestamp = ((CommentItem)o).getMSFE_Long();
	    int x = othertimestamp.compareTo(getMSFE_Long());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}
}