
package co.ords.w;

import java.sql.Timestamp;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

@DynamoDBTable(tableName="words_likes") // specifically, *comment* likes
public class LikeItem implements java.lang.Comparable<LikeItem> {

	private String id;
	private String author_id;
	private String parent;
	private String ip_address;
	private String hpqsp;
	private String url_when_created;
	private long msfe; 
	private String timestamp_hr;
	
	@DynamoDBHashKey(attributeName="id") 
	public String getId() {return id; }
	public void setId(String id) { this.id = id; }
	
	@DynamoDBIndexHashKey(attributeName="parent", globalSecondaryIndexName="parent-index")
	@DynamoDBIndexRangeKey(attributeName="parent", globalSecondaryIndexName="author_id-parent-index")  
	public String getParent() {return parent; }
	public void setParent(String parent) { this.parent = parent; }
	
	@DynamoDBIndexHashKey( attributeName="author_id", globalSecondaryIndexNames={"author_id-msfe-index","author_id-parent-index"})   
	public String getAuthorId() {return author_id; }
	public void setAuthorId(String author_id) { this.author_id = author_id; }
	
	@DynamoDBAttribute(attributeName="ip_address")  
	public String getIPAddress() {return ip_address; }
	public void setIPAddress(String ip_address) { this.ip_address = ip_address; }
	
	@DynamoDBAttribute(attributeName="url_when_created")  
	public String getURLWhenCreated() {return url_when_created; }
	public void setURLWhenCreated(String url_when_created) { this.url_when_created = url_when_created; }
	
	@DynamoDBAttribute(attributeName="hpqsp")  
	public String getHPQSP() {return hpqsp; }
	public void setHPQSP(String hpqsp) { this.hpqsp = hpqsp; }
	
	@DynamoDBAttribute(attributeName="msfe")  
	@DynamoDBIndexRangeKey(attributeName="msfe", globalSecondaryIndexName="author_id-msfe-index")  
	public long getMSFE() {return msfe; }
	public void setMSFE(long msfe) { this.msfe = msfe; }
	
	@DynamoDBAttribute(attributeName="timestamp_hr")  
	public String getTimestampHumanReadable() {return timestamp_hr; }  // note this should not be used. Always format and return the msfe value instead.
	public void setTimestampHumanReadable(String timestamp_hr) { this.timestamp_hr = timestamp_hr; }
	
	@DynamoDBIgnore
	public JSONObject getJSONObject(WordsMapper mapper, DynamoDBMapperConfig dynamo_config)
	{
		JSONObject return_jo = new JSONObject();
		try {
			return_jo.put("id", getId());
			return_jo.put("msfe", getMSFE());
			Timestamp ts = new Timestamp(getMSFE());
			return_jo.put("time_ago", Global.agoIt(ts));
			UserItem useritem = mapper.load( UserItem.class, getAuthorId(), dynamo_config);
			return_jo.put("author_screenname", useritem.getScreenname());
			return_jo.put("author_avatar_icon", useritem.getAvatarIcon());
			return_jo.put("ip_address", getIPAddress());
			return_jo.put("parent", getParent());
			//CommentItem commentitem = mapper.load(CommentItem.class, getParent(), dynamo_config);
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
			//return_jo.put("url_when_created", commentitem.getURLWhenCreated());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return return_jo;
	}
	
	@DynamoDBIgnore
	public int compareTo(LikeItem o) // this makes more recent objects come first
	{
	    Long othertimestamp = ((LikeItem)o).getMSFE();
	    int x = othertimestamp.compareTo(getMSFE());
	    if(x >= 0) // this is to prevent equals
	    	return 1;
	    else
	    	return -1;
	}

}
